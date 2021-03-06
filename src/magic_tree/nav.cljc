(ns magic-tree.nav
  (:refer-clojure :exclude [range])
  (:require [fast-zip.core :as z]
            [magic-tree.node :as n]
            [magic-tree.emit :as emit]
            [magic-tree.range :as range]))

(defn include-prefix-parents [loc]
  (if (emit/prefix-parent? (some-> (z/up loc) (z/node) :tag))
    (include-prefix-parents (z/up loc))
    loc))

(defn child-locs [loc]
  (take-while identity (iterate z/right (z/down loc))))
(defn right-locs [loc]
  (take-while identity (iterate z/right (z/right (include-prefix-parents loc)))))
(defn left-locs [loc]
  (take-while identity (iterate z/left (z/left (include-prefix-parents loc)))))

(defn navigate
  "Navigate to a position within a zipper (returns loc) or ast (returns node)."
  [ast pos]
  (if (map? ast)
    (when (range/within? ast pos)
      (if
        (or (n/terminal-node? ast) (not (seq (get ast :value))))
        ast
        (or (some-> (filter #(range/within? % pos) (get ast :value))
                    first
                    (navigate pos))
            (when-not (= :base (get ast :tag))
              ast))))
    (let [loc ast
          {:keys [value] :as node} (z/node loc)
          found (when (range/within? node pos)
                  (if
                    (or (n/terminal-node? node) (not (seq value)))
                    loc
                    (or
                      (some-> (filter #(range/within? % pos) (child-locs loc))
                              first
                              (navigate pos))
                      ;; do we want to avoid 'base'?
                      loc #_(when-not (= :base (get node :tag))
                              loc))))]
      (if (let [found-node (some-> found z/node)]
            (and (= (get pos :line) (get found-node :end-line))
                 (= (get pos :column) (get found-node :end-column))))
        (or (z/right found) found)
        found))))

(defn mouse-eval-region
  "Select sexp under the mouse. Whitespace defers to parent."
  [loc]
  (or (and (n/sexp? (z/node loc)) loc)
      (z/up loc)))

(defn top-loc [loc]
  (loop [loc loc]
    (if-not loc
      loc
      (if (or (= :base (:tag (z/node loc)))
              (= :base (some-> (z/up loc) z/node :tag)))
        loc
        (recur (z/up loc))))))

(defn closest [pred loc]
  (if-not loc
    nil
    (if (pred loc)
      loc
      (recur pred (z/up loc)))))