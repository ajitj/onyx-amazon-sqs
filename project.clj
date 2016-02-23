(defproject onyx-sqs "0.8.2.0-SNAPSHOT"
  :description "Onyx plugin for Amazon SQS"
  :url "FIX ME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.onyxplatform/onyx "0.8.12-SNAPSHOT"]
                 [com.amazonaws/aws-java-sdk "1.10.49"]]
  :global-vars  {*warn-on-reflection* true}
  :profiles {:dev {:dependencies [[amazonica "0.3.50"]]
                   :plugins []}})
