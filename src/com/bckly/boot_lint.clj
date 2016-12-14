(ns com.bckly.boot-lint
  (:require [boot.core :as boot :refer [deftask]]
            [boot.pod :as pod]
            [clojure.java.io :as io]))

(deftask test-task []
  (boot/with-pre-wrap fileset
    (println (map boot/tmp-file (boot/by-ext [".clj"] (boot/input-files fileset))))
    fileset))

(defn clj-files [fileset]
  (->> fileset
       boot/input-files
       (boot/by-ext [".clj"])))


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
