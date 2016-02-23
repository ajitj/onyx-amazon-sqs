(ns onyx.plugin.sqs-input
  (:require [onyx.peer.function :as function]
            [onyx.peer.pipeline-extensions :as p-ext]
            [onyx.plugin.sqs :as sqs]
            [onyx.static.default-vals :refer [defaults arg-or-default]]
            [onyx.types :as t]
            [onyx.plugin.tasks.sqs :refer [SQSInputTaskMap]]
            [schema.core :as s]
            [taoensso.timbre :refer [debug info] :as timbre])
  (:import [com.amazonaws.services.sqs AmazonSQS AmazonSQSClient AmazonSQSAsync AmazonSQSAsyncClient]))

(defrecord SqsInput 
  [max-pending batch-size batch-timeout pending-messages ^AmazonSQS client queue-url idle-backoff-ms attribute-names max-wait-time-secs]
  p-ext/Pipeline
  (write-batch 
    [this event]
    (function/write-batch event))

  (read-batch [_ event]
    (let [pending (count @pending-messages)
          max-segments (min (- max-pending pending) batch-size)
          received (sqs/receive-messages client queue-url max-segments attribute-names max-wait-time-secs)
          batch (map #(t/input (java.util.UUID/randomUUID) %) received)]
      (if (empty? batch)
        (Thread/sleep idle-backoff-ms)
        (doseq [m batch]
          (swap! pending-messages assoc (:id m) (:message m))))
      {:onyx.core/batch batch}))

  (seal-resource [this event])

  p-ext/PipelineInput
  (ack-segment [_ _ segment-id]
    (->> (@pending-messages segment-id)
         :receipt-handle
         (sqs/delete-message-async client queue-url))
    (swap! pending-messages dissoc segment-id))

  (retry-segment 
    [_ event segment-id]
    (let [message-id (:message-id (@pending-messages segment-id))] 
      (sqs/change-visibility-request-async client queue-url message-id))
    (swap! pending-messages dissoc segment-id))

  (pending?
    [_ _ segment-id])

  (drained? 
    [_ _]
    ;; Cannot safely drain an SQS queue via :done, as there may be pending retries
    false))

(defn input [event]
  (let [task-map (:onyx.core/task-map event)
        _ (s/validate SQSInputTaskMap task-map)
        max-pending (arg-or-default :onyx/max-pending task-map)
        batch-size (:onyx/batch-size task-map)
        batch-timeout (arg-or-default :onyx/batch-timeout task-map)
        pending-messages (atom {})
        client ^AmazonSQS (sqs/new-async-client) 
        queue-url (sqs/get-queue-url client (:sqs/queue-name task-map))
        idle-backoff-ms (:sqs/idle-backoff-ms task-map)
        {:keys [sqs/idle-backoff-ms sqs/attribute-names]} task-map
        max-wait-time-secs (int (/ batch-timeout 1000))]
    (->SqsInput max-pending batch-size batch-timeout pending-messages client queue-url idle-backoff-ms 
                attribute-names max-wait-time-secs)))
