(ns stedi.cdk
  (:require [stedi.cdk.jsii.client :as client]))

(defmacro defc [name construct-fqn args & body]
  `(defn ~name
     [& ~'args]
     (let [instance#    (client/create-object ~construct-fqn ~'args)
           initializer# (fn ~(conj args '& '_) ~@body)]
       (apply initializer# instance# ~'args)
       instance#)))

(defmacro defapp [name args & body]
  `(let [app#         (client/create-object "@aws-cdk/core.App" [])
         initializer# (fn ~args ~@body)]
     (initializer# app#)
     (def ~name app#)))

(defn synth [app]
  (client/call-method app "synth" []))

(defn $ [fqn prop]
  (client/get-static-property-value fqn prop))

(comment
  (defc lambda "@aws-cdk/aws-lambda.Function" [])
  (defc code "@aws-cdk/aws-lambda.AssetCode" [])
  (defc stack "@aws-cdk/core.Stack" [this]
    (lambda this "Fn" {"code"    (code "foo")
                       "handler" "foo"
                       "runtime" ($ "@aws-cdk/aws-lambda.Runtime" "JAVA_8")}))
  (defapp app [this]
    (stack this "hello"))

  (synth app)

  )
