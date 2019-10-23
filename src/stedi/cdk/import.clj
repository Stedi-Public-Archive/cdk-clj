(ns stedi.cdk.import
  "For internal use only. Implementation for import api."
  (:require [clojure.walk :as walk]
            [stedi.cdk.impl :as impl]
            [stedi.cdk.jsii.client :as client]))

(defn- render-docs [docs]
  (str "\nStability: [" (:stability docs) "]"
       "\n\n"
       "Summary:\n\n"
       (:summary docs)
       "\n\n"
       "Remarks:\n\n"
       (:remarks docs)))

(defn- fqn->module
  [fqn]
  (-> fqn (clojure.string/split #"\.") (first)))

(defn- manifest [fqn]
  (-> fqn
      (fqn->module)
      (client/get-manifest)
      (get-in ["types" fqn])
      (walk/keywordize-keys)))

(defn- intern-method
  [{:keys [static parameters docs name ns-sym fqn] :as intern-args}]
  (if static
    (intern ns-sym
            (with-meta (symbol name)
              {:doc      (render-docs docs)
               :arglists (list (mapv (comp symbol :name) parameters))})
            (fn [& args]
              (try
                (let [cdk-class (impl/wrap-class fqn)]
                  (apply impl/invoke-class cdk-class (keyword name) (or args [])))
                (catch Exception e
                  (throw (ex-info "static-method-call-failed"
                                  {:class       fqn
                                   :method-name name
                                   :args        args
                                   :intern-args intern-args} e))))))
    (intern ns-sym
            (with-meta (symbol name)
              {:doc      (render-docs docs)
               :arglists (list (vec (cons 'this (mapv (comp symbol :name) parameters))))})
            (fn [this & args]
              (try
                (impl/invoke-object this (keyword name) (or args []))
                (catch Exception e
                  (throw (ex-info "instance-method-call-failed"
                                  {:class       fqn
                                   :instance    this
                                   :method-name name
                                   :args        args
                                   :intern-args intern-args} e))))))))

(defn- intern-initializer
  [{:keys [fqn parameters alias*]}]
  (ns-unmap *ns* alias*)
  (intern *ns*
          (with-meta alias*
            {:arglists (list (mapv (comp symbol :name) parameters))
             :private  true
             :doc      (with-out-str
                         (println)
                         (clojure.pprint/pprint (manifest fqn)))})
          (impl/wrap-class fqn)))

(defn- intern-enum-member
  [{:keys [ns-sym name fqn]}]
  (intern ns-sym
          (symbol name)
          {"$jsii.enum" (str fqn "/" name)}))

(defn- classes [fqn]
  (let [manifest* (manifest fqn)]
    (lazy-cat [manifest*]
              (when-let [base (:base manifest*)]
                (classes base)))))

(defn import-as-namespace
  [fqn alias*]
  (let [module         (fqn->module fqn)
        module-ns      (-> fqn (impl/package->ns-sym) (create-ns))
        ns-sym         (ns-name module-ns)
        _              (client/load-module module)
        {:keys [initializer
                members
                docs]} (manifest fqn)]
    (doseq [method (mapcat :methods (reverse (classes fqn)))]
      (intern-method (merge method
                            {:ns-sym ns-sym
                             :fqn    fqn})))
    (intern-initializer (merge initializer
                               {:ns-sym ns-sym
                                :fqn    fqn
                                :docs   docs
                                :alias* alias*}))
    (doseq [member members]
      (intern-enum-member (merge member
                                 {:ns-sym ns-sym
                                  :fqn    fqn})))
    (alias alias* ns-sym)))
