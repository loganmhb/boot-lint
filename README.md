# boot-lint

An extensible linting library for the Boot build tool.

Not much there there. Mostly a macro (`linter`) and a convention for storing linting data on fileset metadata, plus implementations for kibit and ancient-clj.

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
```

Alternatively, you can define any boot task you want that attaches data to the `:com.bckly.boot-lint/reports` key of the fileset's metadata (hopefully under your own namespaced key, to avoid conflicts) and the use the `fail` task to fail builds (TODO: fail conditionally on linters).

## Gotchas

Since the linter body is evaluated in a pod, it won't have access to Boot functions or the executing environment, except what you pass in with the unquote operator - see the handling of the dependencies in the above ancient example. It's best to pull all the information you need out of the Boot environment outside the `linter` block, if possible. You *do* have access to the fileset through the binding you provide, however, and that can be used in unquoted expressions as in this Kibit example:

```clojure
(deftask kibit []
  (linter ::kibit fileset '[[jonase/kibit "0.1.3"]]
          (require '[kibit.check :as kibit])
          (->> ~(mapv (comp #(.getAbsolutePath %)
                            boot/tmp-file)
                      (clj-files fileset))
               (map (juxt identity kibit/check-file))
               (into {})
               seq)))
```