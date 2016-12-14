(ns com.bckly.boot-lint
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]))


(defn clj-files
  "Returns a set of all TmpFiles with the `.clj' extension."
  [fileset]
  (->> fileset
       boot/input-files
       (boot/by-ext [".clj"])))


(defmacro linter
  "Defines a linter (a Boot task) which executes in a pod with its
  dependencies. The body of the linter is a template, which will be
  executed in a `boot.pod/with-eval-in' form. It should return nil if
  everything looks good; otherwise, whatever it returns will be
  attached to reports map (which is stored in the fileset metadata under the key
  `:com.bckly.boot-lint/reports') with the key `kw'."
  [kw fs-sym deps & body]
  `(boot.core/with-pre-wrap ~fs-sym
     (let [pod# (pod/make-pod (update (boot.core/get-env)
                                      :dependencies
                                      (partial into)
                                      ~deps))
           report# (pod/with-eval-in pod# ~@body)]
       (if report#
         (with-meta ~fs-sym
           (assoc-in (meta ~fs-sym) [::reports ~kw]
                     report#))
         ~fs-sym))))


(deftask ancient [s snapshots bool "Include snapshot versions"
                  q qualified bool "Include qualified (e.g. alpha) versions"]
  (let [opts {:snapshots? snapshots
              :qualified? qualified}
        deps (:dependencies (boot/get-env))]
    (linter
        ::ancient
        fileset
      '[[ancient-clj "0.3.14"]
        [slingshot "0.12.2"]]
      (require '[ancient-clj.core :as ancient])
      (let [outdated (filter #(ancient/artifact-outdated? % ~opts)
                             ~deps)]
        (when (seq outdated)
          (doseq [dep outdated]
            (println dep "is outdated:"
                     (:version-string (ancient/latest-version! dep ~opts))
                     "is available." ))
          outdated)))))


(deftask kibit []
  (linter ::kibit fileset '[[jonase/kibit "0.1.3"]]
          (require '[kibit.check :as kibit])
          (->> ~(mapv (comp #(.getAbsolutePath %)
                            boot/tmp-file)
                      (clj-files fileset))
               (map (juxt identity kibit/check-file))
               (into {})
               seq)))


(deftask lint
  "Fail if any linters have attached reports."
  []
  (boot/with-post-wrap fileset
    (when (seq (::reports (meta fileset)))
      (throw (Exception. "Some linters failed!")))))
