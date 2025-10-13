// Firebase App (the core Firebase SDK) is always required and
// must be listed before other Firebase SDKs
import firebase from "firebase";

// Add the Firebase services that you want to use
import "firebase/auth";
import "firebase/firestore";

import {ChatMessageModel} from "../model/ChatMessageModel";

const firebaseConfig = {
  apiKey: "AIzaSyA-1ecHEOIkakR2Z8SReYtMHvJKtagSg94",
  authDomain: "personal-fighter.firebaseapp.com",
  databaseURL: "https://personal-fighter-default-rtdb.firebaseio.com",
  projectId: "personal-fighter",
  storageBucket: "personal-fighter.appspot.com",
  messagingSenderId: "587793107253",
  appId: "1:587793107253:web:3f78756fe58da1b112e47c",
  measurementId: "G-CGR8MRFMX0",
};


firebase.initializeApp(firebaseConfig);

const pushMessage = (orderId: string, messages: ChatMessageModel[]) => {
  firebase.database().ref("chats").child(orderId).child("messages").set(messages);
};

export {firebase, pushMessage};
