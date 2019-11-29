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

(defmacro with-url-spy
  "Evaluates `form` in the context of a redefintion of browse-url, capturing the
  arguments and returning them."
  [form]
  `(let [spy# (atom [])]
     (with-redefs [clojure.java.browse/browse-url
                   (fn [url#] (swap! spy# conj url#))]
       (do ~form
           (deref spy#)))))

(deftest browse-test
  (testing "opening documentation with no arguments"
    (is (= ["https://docs.aws.amazon.com/cdk/api/latest"]
           (with-url-spy (cdk/browse)))))
  (testing "opening module documentation with full path"
    (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/core-readme.html"]
           (with-url-spy (cdk/browse "@aws-cdk/core")))))
  (testing "opening module documentation for `core`"
    (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/core-readme.html"]
           (with-url-spy (cdk/browse "core")))))
  (testing "opening fully qualified documentation for a class"
    (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/@aws-cdk_core.Stack.html"]
           (with-url-spy (cdk/browse "@aws-cdk/core.Stack")))))
  (testing "opening documentation for a class"
    (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/@aws-cdk_core.Stack.html"]
           (with-url-spy (cdk/browse Stack)))))
  (testing "opening documentation for an instance"
    (is (= ["https://docs.aws.amazon.com/cdk/api/latest/docs/@aws-cdk_core.Stack.html"]
           (with-url-spy (cdk/browse (Stack)))))))
