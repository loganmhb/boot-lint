# boot-lint

An extensible linting library for the Boot build tool.

## Rationale

Existing linting libraries are difficult to extend and tend not to make their linting data available to the end user. I wanted a linting tool which offered the building blocks for writing linting tasks, covering hairy details like executing the lint in a pod to avoid dependency issues, while allowing the end user to access thelinting data if they so desired.

## API

The provided `linter` macro creates a Boot task which will execute its body in a pod and attach results to the :com.bckly.boot-lint/reports key on the fileset's metadata, where it can be accessed by other tasks.

Example usage:

```clojure
(deftask ancient [s snapshots bool "Include snapshot versions"
                  q qualified bool "Include qualified (e.g. alpha) versions"]
  (let [opts {:snapshots? snapshots
              :qualified? qualified}
        deps (:dependencies (boot/get-env))]
    (linter
        ::ancient ;; key under which results will be registered
        fileset   ;; binding for the fileset
      '[[ancient-clj "0.3.14"] ;; dependencies added to the linter's pod
        [slingshot "0.12.2"]]
      ;; The body of the linter, which is a template:
      (require '[ancient-clj.core :as ancient])
      (let [outdated (filter #(ancient/artifact-outdated? % ~opts)
                             ~deps)]
        (when (seq outdated)
          (doseq [dep outdated]
            (println dep "is outdated:"
                     (:version-string (ancient/latest-version! dep ~opts))
                     "is available." ))
          outdated)))))