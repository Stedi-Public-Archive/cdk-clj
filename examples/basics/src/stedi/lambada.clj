(ns stedi.lambada
  (:require [uswitch.lambada.core :refer [deflambdafn]])
  (:import [software.amazon.awssdk.core.sync RequestBody]
           [software.amazon.awssdk.services.s3 S3Client]
           [software.amazon.awssdk.services.s3.model PutObjectRequest GetObjectRequest]))

(def s3 (S3Client/create))

(deflambdafn stedi.cdk.basics.Hello
  [in out ctx]
  (let [bucket-name (System/getenv "BUCKET")
        object-key  (str (java.util.UUID/randomUUID))]
    (.putObject s3
                (.build
                  (doto (PutObjectRequest/builder)
                    (.bucket bucket-name)
                    (.key object-key)))
                (RequestBody/fromString "Hello conj 2019!"))
    (let [object (.getObject s3
                             (.build
                               (doto (GetObjectRequest/builder)
                                 (.bucket bucket-name)
                                 (.key object-key))))]
      (.write out (.getBytes (str (slurp object) "\n"))))))
