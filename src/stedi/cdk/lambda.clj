(ns stedi.cdk.lambda
  (:require [clojure.string :as string]
            [stedi.cdk :as cdk]
            [stedi.cdk.lambda.build :as build]))

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
  (let [{:keys [lib-layer src]} (build/build aot)

        env       (merge environment
                         (when fn {"STEDI_LAMBDA_HANDLER" (-> fn symbol str)}))
        function  (Function parent id
                            {:code        (AssetCode src)
                             :handler     (or handler "stedi.lambda.entrypoint::handler")
                             :runtime     (:JAVA_8 Runtime)
                             :environment env
                             :memorySize  2048
                             :timeout     (Duration/minutes 1)})
        lib-layer (LayerVersion function "lib-layer" {:code (AssetCode lib-layer)})]
    (Function/addLayers function lib-layer)
    function))

(defn ^:deprecated clj
  "Deprecated in favor of `stedi.cdk.lambda/Clj`."
  [_ parent id {:keys [fn environment handler aot] :as props}]
  (Clj parent id props))
