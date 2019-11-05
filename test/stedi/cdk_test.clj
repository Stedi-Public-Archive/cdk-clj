(ns stedi.cdk-test
  "Provides examples of interacting with CDK constructs and classes."
  (:require [clojure.test :refer [deftest is testing]]
            [stedi.cdk :as cdk]))

(cdk/import ["@aws-cdk/core"
             Stack
             App :as A]
            ["@aws-cdk/aws-lambda" Runtime])

(deftest cdk-example-test
  (testing "instantiating an object"
    (is (Stack nil "my-stack")))
  (testing "getting a property of an instance"
    (is (:region (Stack nil "my-stack"))))
  (testing "calling an instance method"
    (is (Stack/toString (Stack nil "my-stack"))))
  (testing "calling a static method"
    (is (Stack/isStack (Stack nil "my-stack"))))
  (testing "getting a static property of a class"
    (is (:JAVA_8 Runtime)))
  (testing "renaming an alias"
    (is (A/isApp (A)))))
