(ns com.bckly.test
  (:require  [clojure.test :as t]))

(defn bad-fn []
  (= 0 1))
