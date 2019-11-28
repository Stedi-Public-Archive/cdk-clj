(ns stedi.cdk.alpha-test
  "Provides examples of interacting with CDK constructs and classes."
  (:require [clojure.test :refer [deftest is testing]]
            [stedi.cdk.alpha :as cdk]))

(cdk/import [[App :as A, Stack] :from "@aws-cdk/core"]
            [[Function Runtime] :from "@aws-cdk/aws-lambda"]
            [[StringParameter] :from "@aws-cdk/aws-ssm"])

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
    (is (A/isApp (A))))
  (testing "calling a method on a returned interface"
    (let [s (Stack)]
      (is (StringParameter/grantRead
            (StringParameter/fromStringParameterName s "param" "param-name")
            (Function/fromFunctionArn s "fn" "function-arn"))))))

(deftest browse-test
  (testing "opening documentation with no arguments"
    (let [spy (atom [])]
      (with-redefs [clojure.java.browse/browse-url
                    (fn [url] (swap! spy conj url))]
        (cdk/browse)
        (is (= ["https://docs.aws.amazon.com/cdk/api/latest"] @spy)))))
  (testing "opening module documentation with full path"
    (let [spy (atom [])]
      (with-redefs [clojure.java.browse/browse-url
                    (fn [url] (swap! spy conj url))]
        (cdk/browse "@aws-cdk/core")
        (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/core-readme.html"]
               @spy)))))
  (testing "opening module documentation for `core`"
    (let [spy (atom [])]
      (with-redefs [clojure.java.browse/browse-url
                    (fn [url] (swap! spy conj url))]
        (cdk/browse "core")
        (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/core-readme.html"]
               @spy)))))
  (testing "opening fully qualified documentation for a class"
    (let [spy (atom [])]
      (with-redefs [clojure.java.browse/browse-url
                    (fn [url] (swap! spy conj url))]
        (cdk/browse "@aws-cdk/core.Stack")
        (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/@aws-cdk_core.Stack.html"]
               @spy)))))
  (testing "opening documentation for a class"
    (let [spy (atom [])]
      (with-redefs [clojure.java.browse/browse-url
                    (fn [url] (swap! spy conj url))]
        (cdk/browse Stack)
        (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/@aws-cdk_core.Stack.html"]
               @spy)))))
  (testing "opening documentation for an instance"
    (let [spy (atom [])]
      (with-redefs [clojure.java.browse/browse-url
                    (fn [url] (swap! spy conj url))]
        (cdk/browse (Stack))
        (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/@aws-cdk_core.Stack.html"]
               @spy))))))
