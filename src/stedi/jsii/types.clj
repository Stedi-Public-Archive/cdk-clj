(ns stedi.jsii.types
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [stedi.jsii.assembly :as assm]))

(defn- member-values
  [fqn]
  (->> (:members (assm/get-type fqn))
       (map :name)
       (map keyword)
       (set)))

(deftype JsiiObject [fqn ref]
  json/JSONWriter
  (-write [object out]
    (.write out (format "{\"$jsii.byref\": \"%s\"}" ref))))

(deftype JsiiClass [fqn])

(deftype JsiiEnumMember [fqn value]
  json/JSONWriter
  (-write [object out]
    (.write out (format "{\"$jsii.enum\": \"%s\"}"
                        (str fqn "/" (name value))))))

(deftype JsiiEnum [fqn]
  clojure.lang.ILookup
  (valAt [_ k]
    (let [valid-values (member-values fqn)
          value
          (or (valid-values k)
              (throw (ex-info "Invalid enumeration value"
                              {:k            k
                               :valid-values valid-values})))]
      (JsiiEnumMember. fqn value))))

(defn get-assembly
  [t]
  (assm/get-type (.-fqn t)))

(defn class-instance?
  [fqn x]
  (and (instance? JsiiObject x)
       (= fqn (.-fqn x))))

(defn gen-class-instance
  [fqn]
  (sgen/return (JsiiObject. fqn nil)))

(defn enum-member?
  [fqn x]
  (and (instance? JsiiEnumMember x)
       (= fqn (.-fqn x))
       ((member-values fqn) (.-value x))))

(defn gen-enum-member
  [fqn]
  (sgen/bind
    (sgen/elements (member-values fqn))
    (fn [value]
      (sgen/return
        (JsiiEnumMember. fqn value)))))

(defn gen-satisfies-interface
  [fqn]
  (sgen/return (JsiiObject. fqn nil)))

(defn satisfies-interface?
  [fqn x]
  (and (instance? JsiiObject x)
       (let [{:keys [interfaces]} (get-assembly x)]
         (some #{fqn} interfaces))))
