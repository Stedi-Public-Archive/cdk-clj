(ns stedi.app.sample
  (:require [cheshire.core :as json]
            [stedi.lambda :refer [defentrypoint]]))

(defentrypoint handler
  (fn [_]
    {:output
     (json/generate-string
       {:statusCode 200
        :body       "Hello world"})}))
