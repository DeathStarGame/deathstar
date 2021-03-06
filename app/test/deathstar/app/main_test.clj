(ns deathstar.app.main-test
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.set :refer [subset?]]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sgen]
   [clojure.spec.test.alpha :as stest]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [clojure.test :refer [is run-all-tests testing deftest run-tests]]

   [deathstar.app.main]))

(comment

  (run-tests)
  (run-all-tests #"app.*")
  (re-matches #"app.*" "deathstar.app.main-test")
  (stest/check)
  (tc/quick-check)

  ;;
  )

(deftest arithmetic
  (testing "Arithmetic"
    (testing "with positive integers"
      (is (= 4 (+ 2 2)))
      (is (= 7 (+ 3 4))))
    (testing "with negative integers"
      (is (= -4 (+ -2 -2)))
      (is (= -1 (+ 3 -4))))))

(deftest foobar
  (testing "foo and bar"
    (testing "is foo"
      (is (= :foo :foo)))
    (testing "is bar"
      (is (= :bar :baz)))))