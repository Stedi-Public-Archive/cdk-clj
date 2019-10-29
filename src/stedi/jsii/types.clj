(ns stedi.jsii.types
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.walk :as walk]
            [stedi.jsii.assembly :as assm]
            [stedi.jsii.client :as client])
  (:import (software.amazon.jsii JsiiObjectRef)))

(defn- jsii-type?
  [x]
  (and (map? x)
       (string? (ffirst x))
       (.startsWith (ffirst x) "$jsii")))

(defmulti ->type ffirst)

(defn- ->clj
  [m]
  (walk/postwalk
    (fn [x]
      (if (jsii-type? x)
        (->type x)
        x))
    m))

(defprotocol Invocable
  (-invoke [_ req]))

(defn get-type-info
  [t]
  (assm/get-type (.-fqn t)))

(defn- member-values
  [fqn]
  (->> (:members (assm/get-type fqn))
       (map :name)
       (map keyword)
       (set)))

(defn- props
  [x]
  (into #{}
        (comp (map :name)
              (map keyword))
        (:properties (get-type-info x))))

(deftype JsiiObject [fqn ref]
  clojure.lang.ILookup
  (valAt [this k]
    (let [valid-props (props this)
          value
          (or (valid-props k)
              (throw (ex-info "Invalid property"
                              {:k           k
                               :valid-props valid-props})))]
      (->clj (client/get-property-value ref (name value)))))

  Invocable
  (-invoke [_ {:keys [op args]}]
    (->clj (client/call-method ref (name op) (or args []))))

  java.lang.Object
  (toString [this]
    (when (some (comp #{"toString"} :name)
                (:methods (get-type-info this)))
      (-invoke this {:op :toString})))

  json/JSONWriter
  (-write [object out]
    (.write out (format "{\"$jsii.byref\": \"%s\"}" ref))))

(defmethod ->type "$jsii.byref"
  [x]
  (let [id  (-> x first second)
        ref (JsiiObjectRef/fromObjId id)]
    (->JsiiObject (.getFqn ref) id)))

(defmethod print-method JsiiObject
  [this w]
  (.write w (format "#jsii-object[%s]"
                    (pr-str {:id    (.-ref this)
                             :props (props this)}))))

(deftype JsiiClass [fqn]
  clojure.lang.ILookup
  (valAt [this k]
    (let [valid-props (props this)
          value
          (or (valid-props k)
              (throw (ex-info "Invalid property"
                              {:k           k
                               :valid-props valid-props})))]
      (->clj (client/get-static-property-value fqn (name value)))))

  Invocable
  (-invoke [_ {:keys [op args]}]
    (->clj (client/call-static-method fqn (name op) (or args [])))))

(defmethod print-method JsiiClass
  [this w]
  (.write w (format "#jsii-class[%s]"
                    (pr-str {:fqn   (.-fqn this)
                             :props (props this)}))))

(deftype JsiiEnumMember [fqn value]
  json/JSONWriter
  (-write [object out]
    (.write out (format "{\"$jsii.enum\": \"%s\"}"
                        (str fqn "/" (name value))))))

(defmethod print-method JsiiEnumMember
  [this w]
  (.write w (format "#jsii-enum-member[%s]"
                    (pr-str (str (.-fqn this) "/" (name (.-value this)))))))

(deftype JsiiEnumClass [fqn]
  clojure.lang.ILookup
  (valAt [_ k]
    (let [valid-values (member-values fqn)
          value
          (or (valid-values k)
              (throw (ex-info "Invalid enumeration value"
                              {:k            k
                               :valid-values valid-values})))]
      (JsiiEnumMember. fqn value))))

(defmethod print-method JsiiEnumClass
  [this w]
  (.write w (format "#jsii-enum-class[%s]"
                    (pr-str {:fqn     (.-fqn this)
                             :members (member-values (.-fqn this))}))))

(defn get-class
  [fqn]
  (let [{:keys [kind]} (or (assm/get-type fqn)
                           (throw (Exception. (str "No class available for fqn: " fqn))))]
    (case kind
      "enum"  (->JsiiEnumClass fqn)
      "class" (->JsiiClass fqn))))

(defn create
  [c args]
  (let [fqn (.-fqn c)
        id  (client/create-object fqn (or args []))]
    (->JsiiObject fqn id)))

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
       (let [{:keys [interfaces]} (get-type-info x)]
         (some #{fqn} interfaces))))
