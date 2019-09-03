(ns stedi.cdk-test
  (:require [stedi.cdk :as cdk]
            [clojure.test :refer [deftest is testing]]))

(cdk/require ["@aws-cdk/core" cdk-core])

(deftest cdk-objects-test
  (testing "toString calls out to the jsii object toString method"
    (is (re-matches #"\$\{Token\[TOKEN\.[0-9]+]\}"
                    (str (cdk-core/Fn :getAtt "resource" "attr"))))))
