(ns stedi.cdk.app.sample
  (:require [clojure.spec.alpha :as s]
            [stedi.cdk :as cdk]
            [stedi.app.sample :as sample-app]))

(cdk/import ("@aws-cdk/aws-lambda" Function Runtime AssetCode)
            ("@aws-cdk/aws-iam" Effect)
            ("@aws-cdk/core" Stack))

(defn app-stack [app id]
  (let [stack (Stack app id {})]
    (Function stack "my-fn"
              {:runtime (:JAVA_8 Runtime)
               :code    (AssetCode "./src")
               :handler ""})
    stack))

(cdk/defapp app
  [this]
  (app-stack this "test"))

(comment
  ;; Instantiation:

  ;; old
  (cdk/require ["@aws-cdk/aws-lambda" lambda])
  (lambda/Function :cdk/create stack "myfn" {})

  ;; new
  (cdk/import ("@aws-cdk/aws-lambda" Function))
  (Function stack "my-fn" {})

  ;; Instance Properties (unchanged):
  (:role function)

  ;; Static Properties:

  ;; old
  (cdk/require ["@aws-cdk/aws-lambda" lambda])
  (:JAVA_8 lambda/Runtime)

  ;; new
  (cdk/import ("@aws-cdk/aws-lambda" Runtime))
  (:JAVA_8 Runtime)


  ;; Static Methods:

  ;; old
  (cdk/require ["@aws-cdk/core" cdk-core])
  (cdk-core/Duration :minutes 5)

  ;; new
  (cdk/import ("@aws-cdk/core" Duration))
  (Duration/minutes 5)

  ;; Instance Methods:

  ;; old
  (function :addEnvironment "key" "value")

  ;; new
  (cdk/import ("@aws-cdk/aws-lambda" Function))
  (Function/addEnvironment function "key" "value")

  ;; Enums:

  ;; old
  (cdk/require ["@aws-cdk/aws-iam" iam])
  (iam/Effect :cdk/enum :ALLOW)

  ;; new
  (cdk/import ("@aws-cdk/aws-iam" Effect))
  Effect/ALLOW
  )
