(ns com.bckly.boot-lint
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [clojure.java.io :as io]))


(defn clj-files [fileset]
  (->> fileset
       boot/input-files
       (boot/by-ext [".clj"])))


(defn check-ancient [pod deps opts]
  (pod/with-eval-in pod
    (require '[ancient-clj.core :as ancient])
    (let [outdated (filter #(ancient/artifact-outdated? % ~opts)
                           '~deps)]
      (when (seq outdated)
        (doseq [dep outdated]
          (println dep "is outdated:"
                   (:version-string (ancient/latest-version! dep ~opts))
                   "is available." ))
        outdated))))

(deftask ancient [s snapshots bool "Include snapshot versions"
                  q qualified bool "Include qualified (e.g. alpha) versions"]
  (boot.core/with-pre-wrap fileset
    (let [pod (pod/make-pod (update (boot.core/get-env)
                                    :dependencies
                                    (partial into)
                                    '[[ancient-clj "0.3.14"]
                                      [slingshot "0.12.2"]]))
          report (check-ancient pod (:dependencies (boot.core/get-env))
                                {:snapshots? snapshots
                                 :qualified? qualified
                                 :repositories (:repositories (boot.core/get-env))})
          new-meta (assoc-in (meta fileset)
                             [::reports ::ancient]
                             report)]
      (if report
        (with-meta fileset new-meta)
        fileset))))

(defn check-kibit [pod file]
  (pod/with-eval-in pod
    (require '[kibit.check :as kibit])
    (kibit/check-file ~file)))


(deftask kibit []
  (boot/with-pre-wrap fileset
    (let [pod (pod/make-pod (update (boot/get-env)
                                    :dependencies
                                    conj
                                    '[jonase/kibit "0.1.3"]))
          reports
          (into {} (map (juxt boot/tmp-path 
                              (comp (partial check-kibit pod)
                                    #(.getAbsolutePath %)
                                    boot/tmp-file))
                        (clj-files fileset)))
          new-meta (assoc-in (meta fileset)
                             [::reports ::kibit]
                             reports)]
      (if (seq reports)
        (with-meta fileset new-meta)
        fileset))))


(deftask lint
  "Fail if any linters have attached reports."
  []
  (boot/with-post-wrap fileset
    (when (seq (::reports (meta fileset)))
      (throw (Exception. "Some linters failed!")))))
