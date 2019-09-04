(ns stedi.cdk.app.sample
  (:require [clojure.spec.alpha :as s]
            [stedi.cdk :as cdk]
            [stedi.app.sample :as sample-app]))

(cdk/import ("@aws-cdk/aws-lambda" Function Runtime AssetCode)
            ("@aws-cdk/aws-iam" Effect)
            ("@aws-cdk/core" Stack))

(defn app-stack [app id]
  (let [stack (Stack nil id {})]
    (Function stack "my-fn"
              {:runtime (:JAVA_8 Runtime)
               :code    (AssetCode "./src")
               :handler ""})
    stack))

(cdk/defapp app
  [this]
  (app-stack this "test"))

(comment
  (Function stack "my-fn" {}) ;; constructor
  (:role function)            ;; instance prop
  (:JAVA_8 Runtime)           ;; static prop
  (Function/fromFunctionArn)  ;; static method
  (Function/toString this)    ;; instance method
  Effect/ALLOW                ;; enum
  )

#_(def app
  (let [app   (app/create)
        stack (stack/create app "foobar")]
    (function/create stack "fn"
                     {:code    (asset-code/create "./src")
                      :handler "foo.bar"
                      :runtime (runtime/JAVA_8)})
    app))
