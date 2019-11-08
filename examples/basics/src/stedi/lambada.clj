(ns stedi.lambada
  (:require [uswitch.lambada.core :refer [deflambdafn]]))

(deflambdafn stedi.cdk.basics.Hello
  [in out ctx]
  (let [bucket-name (System/getenv "BUCKET")]
    (.write out (.getBytes (format "Hello! Bucket Name: %s" bucket-name)))))
