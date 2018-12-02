(ns status-im.messages.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :messages/messages
 (fn [db] (:messages db)))
