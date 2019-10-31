(ns stedi.jsii.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [stedi.jsii.assembly :as assm]
            [stedi.jsii.impl :as impl]
            [stedi.jsii.fqn :as fqn]))

(s/def ::string-like
  (s/or :string string?
        :keyword keyword?))

(s/def ::json
  (s/or :string ::string-like
        :int integer?
        :float float?
        :boolean boolean?
        :vector (s/coll-of ::json-value :kind vector?)
        :map (s/map-of ::string-like ::json-value)))

(defn- metatype
  [{:keys [datatype kind]}]
  (cond
    datatype :datatype
    :else    (keyword kind)))

(defmulti ^:private spec-form ffirst)

(defmethod spec-form :union
  [{:keys [union]}]
  (let [{:keys [types]} union]
    `(s/or ~@(mapcat (juxt (constantly :opt)
                           spec-form)
                     types))))

(defmethod spec-form :fqn
  [{:keys [fqn]}]
  (fqn/fqn->qualified-keyword fqn))

(defmethod spec-form :primitive
  [{:keys [primitive]}]
  (case primitive
    "string"  `string?
    "any"     `any?
    "boolean" `boolean?
    "number"  `number?
    "date"    `inst?
    "json"    ::json))

(defmethod spec-form :collection
  [{:keys [collection]}]
  (let [{:keys [elementtype kind]} collection]
    (let [element-form (spec-form elementtype)]
      (case kind
        "map"   `(s/map-of ::string-like ~element-form)
        "array" `(s/coll-of ~element-form :kind vector?)))))

(defn- prop-spec-k
  [t prop]
  (fqn/fqn->qualified-keyword (:fqn t) (:name prop)))

(defn- gen-class-instance
  [fqn]
  (sgen/return (impl/->JsiiObject fqn nil)))

(defn- class-spec-definition
  [{:keys [fqn]}]
  `(s/def ~(fqn/fqn->qualified-keyword fqn)
     (s/spec (partial impl/class-instance? ~fqn)
             :gen (partial gen-class-instance ~fqn))))

(defn- gen-enum-member
  [fqn]
  (sgen/bind
    (sgen/elements (impl/member-values fqn))
    (fn [value]
      (sgen/return
        (impl/->JsiiEnumMember fqn value)))))

(defn- enum-spec-definition
  [{:keys [fqn]}]
  `(s/def ~(fqn/fqn->qualified-keyword fqn)
     (s/spec (partial impl/enum-member? ~fqn)
             :gen (partial gen-enum-member ~fqn))))

(defn- gen-satisfies-interface
  [fqn]
  (sgen/return
    (impl/->JsiiObject fqn nil)))

(defn- interface-spec-definition
  [{:keys [fqn]}]
  `(s/def ~(fqn/fqn->qualified-keyword fqn)
     (s/spec (partial impl/satisfies-interface? ~fqn)
             :gen (partial gen-satisfies-interface ~fqn))))

(defn- datatype-spec-definition
  [{:keys [fqn properties] :as t}]
  (let [req-un (into []
                     (comp (remove :optional)
                           (map (partial prop-spec-k t)))
                     properties)
        opt-un (into []
                     (comp (filter :optional)
                           (map (partial prop-spec-k t)))
                     properties)]
    `(s/def ~(fqn/fqn->qualified-keyword fqn)
       (s/keys :req-un ~req-un
               :opt-un ~opt-un))))

(defn- method-arg-spec-form
  [{:keys [fqn] :as t} {:keys [static parameters] :as method}]
  `(s/cat ~@(concat
              (when-not static
                (list :this (spec-form {:fqn fqn})))
              (mapcat (juxt (comp keyword :name)
                            (fn [{:keys [optional] :as param}]
                              (let [form (spec-form (:type param))]
                                (if optional
                                  `(s/? (s/nilable ~form))
                                  form))))
                      parameters))))

(defn- method-spec-definition
  [{:keys [fqn] :as t} {:keys [name returns] :as method}]
  `(s/fdef ~(fqn/fqn->qualified-symbol fqn name)
                :args ~(method-arg-spec-form t method)
                :ret  ~(or (some-> returns (:type) (spec-form)) `nil?)))

(defn- prop-spec-definition
  [t prop]
  `(s/def ~(prop-spec-k t prop)
     ~(spec-form (:type prop))))

(defn- initializer-spec-definition
  [{:keys [fqn] :as t} method]
  `(s/fdef ~(fqn/fqn->qualified-symbol fqn "impl" "-initializer")
     :args ~(method-arg-spec-form t (assoc method :static true))
     :ret  ~(fqn/fqn->qualified-keyword fqn)))

(defn- class-initializer-spec-definition
  [{:keys [fqn name] :as t} method]
  `(s/fdef ~(fqn/fqn->qualified-symbol fqn "impl" name)
     :args ~(method-arg-spec-form t (assoc method :status true))
     :ret  ~(fqn/fqn->qualified-keyword fqn)))

(defn- spec-definitions
  [t]
  (try
    (let [{:keys [initializer methods properties]} t

          type-definition
          (case (metatype t)
            :class     (class-spec-definition t)
            :enum      (enum-spec-definition t)
            :datatype  (datatype-spec-definition t)
            :interface (interface-spec-definition t))

          initializer-definition            (initializer-spec-definition t initializer)
          class-initializer-spec-definition (class-initializer-spec-definition t initializer)]
      (doall
        (concat (list type-definition
                      initializer-definition
                      class-initializer-spec-definition)
                (map (partial method-spec-definition t) methods)
                (map (partial prop-spec-definition t) properties))))
    (catch Throwable e
      (throw (ex-info "Unable to build spec definition" {:t t} e)))))

(defn- get-ret
  [form]
  (second (drop-while #(not= :ret %) form)))

(defn- unmet-deps
  [ctx definition]
  (case (first definition)
    clojure.spec.alpha/def
    (let [spec-form (nth definition 2)]
      (cond (and (qualified-keyword? spec-form)
                 (not (get-in ctx [:resolved spec-form])))
            [spec-form]))
    
    clojure.spec.alpha/fdef
    (let [ret (get-ret definition)]
      (cond (and (qualified-keyword? ret)
                 (not (get-in ctx [:resolved ret])))
            [ret]))))

(defn- index-unresolved
  [ctx definition deps]
  (let [k (second definition)]
    (-> (reduce
          (fn [ctx' dep]
            (update-in ctx' [:waiting-on dep]
                       #(conj (or % #{}) k)))
          ctx
          deps)
        (assoc-in [:definition k] definition)
        (assoc-in [:deps k] (set deps)))))

(defn- resolve-deps
  [ctx new-k]
  (-> (if-let [possibly-resolved-ks (get-in ctx [:waiting-on new-k])]
        (reduce
          (fn [[ctx' resolved] k]
            (if-let [deps (-> ctx'
                              (get-in [:deps k])
                              (disj new-k)
                              (not-empty))]
              [(assoc-in ctx' [:deps k] deps)
               resolved]
              [(-> ctx'
                   (update :resolved #(conj (or % #{}) k))
                   (update :definition dissoc k)
                   (update :deps dissoc k))
               (conj resolved (get-in ctx' [:definition k]))]))
          [(update ctx :waiting-on dissoc new-k) []]
          possibly-resolved-ks)
        [ctx []])
      (update-in [0 :resolved] #(conj (or % #{}) new-k))))

(defn- load-definition
  [ctx definition]
  (try 
    (if-let [deps (unmet-deps ctx definition)]
      (index-unresolved ctx definition deps)
      (let [k                      (second definition)
            [updated-ctx resolved] (resolve-deps ctx k)]
        (update updated-ctx :forms #(apply conj % definition resolved))))
    (catch Exception e
      (throw (ex-info "Error loading spec definition"
                      {:ctx        ctx
                       :definition definition}
                      e)))))

(defn- load-specs
  [types]
  (let [ctx (reduce load-definition {:resolved #{::json
                                                 ::string-like}}
                    (mapcat spec-definitions types))]
    (select-keys ctx [:resolved :waiting-on])
    (assert (empty? (:waiting-on ctx)) "Some forms couldn't be loaded")
    (dorun (map eval (reverse (:forms ctx))))))

;; Load all specs
(load-specs (assm/all-types))
