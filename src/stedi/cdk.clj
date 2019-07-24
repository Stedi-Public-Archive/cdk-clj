(ns stedi.cdk
  (:require [clojure.java.io :as io])
  (:import (software.amazon.jsii JsiiRuntime JsiiModule JsiiObjectRef Util)
           (com.fasterxml.jackson.databind ObjectMapper)
))



(comment
  (do
    (load-module (module {:module-name    "@aws-cdk/cx-api"
                          :module-version "1.1.0"
                          :module-bundle  "/cx-api@1.1.0.jsii.tgz"}))

    (load-module (module {:module-name    "@aws-cdk/core"
                          :module-version "1.1.0"
                          :module-bundle  "/core@1.1.0.jsii.tgz"}))

    (load-module (module {:module-name    "@aws-cdk/region-info"
                          :module-version "1.1.0"
                          :module-bundle  "/region-info@1.1.0.jsii.tgz"}))

    (load-module (module {:module-name    "@aws-cdk/aws-iam"
                          :module-version "1.1.0"
                          :module-bundle  "/aws-iam@1.1.0.jsii.tgz"}))

    (load-module (module {:module-name    "@aws-cdk/aws-cloudwatch"
                          :module-version "1.1.0"
                          :module-bundle  "/aws-cloudwatch@1.1.0.jsii.tgz"}))

    (load-module (module {:module-name    "@aws-cdk/aws-ssm"
                          :module-version "1.1.0"
                          :module-bundle  "/aws-ssm@1.1.0.jsii.tgz"}))

    (load-module (module {:module-name    "@aws-cdk/aws-ec2"
                          :module-version "1.1.0"
                          :module-bundle  "/aws-ec2@1.1.0.jsii.tgz"}))

    (load-module (module {:module-name    "@aws-cdk/aws-kms"
                          :module-version "1.1.0"
                          :module-bundle  "/aws-kms@1.1.0.jsii.tgz"}))

    (load-module (module {:module-name    "@aws-cdk/aws-sqs"
                          :module-version "1.1.0"
                          :module-bundle  "/aws-sqs@1.1.0.jsii.tgz"}))

    (load-module (module {:module-name    "@aws-cdk/assets"
                          :module-version "1.1.0"
                          :module-bundle  "/assets@1.1.0.jsii.tgz"}))

    #_(load-module (module {:module-name    "@aws-cdk/aws-s3-assets"
                            :module-version "1.1.0"
                            :module-bundle  "/aws-s3-assets@1.1.0.jsii.tgz"}))

    #_(load-module (module {:module-name    "@aws-cdk/aws-lambda"
                            :module-version "1.1.0"
                            :module-bundle  "/aws-lambda@1.1.0.jsii.tgz"})))


  ($ "@aws-cdk/core.Duration" "minutes" 3)

  (.convertValue (ObjectMapper.)
                 (.callStaticMethod (client) "@aws-cdk/core.Duration" "hours" (.valueToTree (ObjectMapper.) [5]))
                 Object)

  (.convertValue (ObjectMapper.)
                 (.callMethod (client)
                              (JsiiObjectRef/fromObjId "@aws-cdk/core.Duration@10010")
                              "toMinutes"
                              (.valueToTree (ObjectMapper.) []))
                 Object)

  (.createObject (client) "@aws-cdk/core.App" [])

  (.pendingCallbacks (client))

  (create! )
  (delete! )
  (get-prop )

  (swap! "" :bar)

  (.getResourceAsStream JsiiModule "/aws-lambda@1.1.0.jsii.tgz")

  (with-open [is (java.util.zip.ZipInputStream. (io/input-stream (io/resource "aws-lambda@1.1.0.jsii.tgz")))]
    (.getNextEntry is))

  

  )
