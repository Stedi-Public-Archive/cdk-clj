(ns stedi.lambda.entrypoint
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn])
  (:gen-class
   :methods [^:static [handler
                       [java.io.InputStream
                        java.io.OutputStream
                        com.amazonaws.services.lambda.runtime.Context]
                       void]]))

(defn- invoke-entrypoint [input]
  (let [f (-> (System/getenv "STEDI_LAMBDA_HANDLER")
              (edn/read-string)
              (requiring-resolve))]
    (f input)))

(defn -handler [is os context]
  (let [input (with-meta (json/read-str (slurp is) :key-fn keyword)
                {::context context})]
      (.write os
              (.getBytes
                (json/write-str
                  (invoke-entrypoint input))))))
