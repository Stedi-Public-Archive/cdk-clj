(ns stedi.cdk.lambda
  (:require [clojure.string :as string]
            [stedi.cdk :as cdk]
            [stedi.cdk.lambda.impl :as impl])
  (:import (java.security MessageDigest)
           (java.util Base64)))

(cdk/require ["@aws-cdk/core" cdk-core]
             ["@aws-cdk/aws-lambda" lambda])

(cdk/defextension clj cdk-core/Construct
  :cdk/init
  (fn [this _name {:keys [fn environment]}]
    (let [path      (get-in this [:node :path])
          build-dir (str "./target/" (string/replace path "/" "_"))
          
          {:keys [lib-layer aot-layer src]} (impl/build-layers build-dir)]
      (lambda/Function :cdk/create this "function"
                       {:code        (lambda/Code :cdk/asset src)
                        :handler     "stedi.cdk.lambda.handler::handler"
                        :runtime     (:JAVA_8 lambda/Runtime)
                        :environment (merge environment
                                            {"STEDI_LAMBDA_ENTRYPOINT" fn})
                        :memorySize  2048
                        :layers
                        [(lambda/LayerVersion :cdk/create this "lib-layer"
                                              {:code (lambda/Code :cdk/asset lib-layer)})
                         (lambda/LayerVersion :cdk/create this "class-layer"
                                              {:code (lambda/Code :cdk/asset aot-layer)})]}))))
