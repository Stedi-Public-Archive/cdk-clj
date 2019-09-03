(ns stedi.cdk.lambda
  (:require [clojure.string :as string]
            [stedi.cdk :as cdk]
            [stedi.cdk.lambda.impl :as impl])
  (:import (java.security MessageDigest)
           (java.util Base64)))

(cdk/require ["@aws-cdk/core" cdk-core]
             ["@aws-cdk/aws-lambda" lambda])

(cdk/defextension clj lambda/Function
  :cdk/build
  (fn [constructor parent id {:keys [fn environment handler aot]}]
    (let [path      (str (get-in parent [:node :path]) "/" id)
          build-dir (str "./target/" (string/replace path "/" "_"))
          
          {:keys [lib-layer aot-layer src]} (impl/build-layers build-dir aot)

          function (constructor parent id
                                {:code        (lambda/Code :cdk/asset src)
                                 :handler     (or handler "stedi.cdk.lambda.handler::handler")
                                 :runtime     (:JAVA_8 lambda/Runtime)
                                 :environment (merge environment
                                                     (when fn {"STEDI_LAMBDA_ENTRYPOINT" (-> fn symbol str)}))
                                 :memorySize  2048
                                 :timeout     (cdk-core/Duration :minutes 1)})]
      (function :addLayers
                (lambda/LayerVersion :cdk/create function "lib-layer"
                                     {:code (lambda/Code :cdk/asset lib-layer)})
                (lambda/LayerVersion :cdk/create function "class-layer"
                                     {:code (lambda/Code :cdk/asset aot-layer)})))))
