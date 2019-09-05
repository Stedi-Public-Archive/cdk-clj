(ns stedi.cdk.lambda
  (:require [clojure.string :as string]
            [stedi.cdk :as cdk]
            [stedi.cdk.lambda.impl :as impl])
  (:import (java.security MessageDigest)
           (java.util Base64)))

(cdk/import ["@aws-cdk/core" Duration]
            ["@aws-cdk/aws-lambda" Function AssetCode Runtime LayerVersion])

(defn Clj
  "Wraps `@aws-cdk/aws-clambda.Function` to be clojure friendly.

    fn          - A var pointing to a handler function.
    environment - A map of environment variables
    handler     - Only necessary if fn isn't specified
    aot         - A list of namespaces to AOT compile
  "
  [parent id {:keys [fn environment handler aot]}]
  (let [path                              (str (get-in parent [:node :path]) "/" id)
        build-dir                         (str "./target/" (string/replace path "/" "_"))
        {:keys [lib-layer aot-layer src]} (impl/build-layers build-dir aot)
        function
        (Function parent id
                  {:code        (AssetCode src)
                   :handler     (or handler "stedi.cdk.lambda.handler::handler")
                   :runtime     (:JAVA_8 Runtime)
                   :environment (merge environment
                                       (when fn {"STEDI_LAMBDA_ENTRYPOINT" (-> fn symbol str)}))
                   :memorySize  2048
                   :timeout     (Duration/minutes 1)})]
    (Function/addLayers function
                        (LayerVersion function "lib-layer" {:code (AssetCode lib-layer)})
                        (LayerVersion function "class-layer" {:code (AssetCode aot-layer)}))
    function))

(defn ^:deprecated clj
  "Deprecated in favor of `stedi.cdk.lambda/Clj`."
  [_ parent id {:keys [fn environment handler aot] :as props}]
  (Clj parent id props))
