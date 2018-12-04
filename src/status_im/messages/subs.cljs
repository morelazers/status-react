(ns status-im.messages.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::messages
 (fn [db]
   (:messages db)))

(re-frame/reg-sub
 :messages/messages
 :<- [::messages]
 (fn [messages]
   (remove #((comp :seen? val) %) messages)))
