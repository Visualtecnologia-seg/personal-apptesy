import React, {useState} from "react";
import {KeyboardAvoidingView, Platform, StyleSheet, Text, TextInput, TouchableOpacity, View} from "react-native";
import {GREY, SCREEN_WIDTH, Theme} from "../../theme";
import {Feather} from "@expo/vector-icons";
import {Button} from "../../components";
import {useNavigation} from "@react-navigation/native";

import {useAuth} from "../../context/AuthContext";
import {getUser} from "../../services/UserService";
import {useUserRecords} from "../../context/UserRecordsContext";

import * as SecureStore from "expo-secure-store";
import ForgotPasswordModal from "./modal/ForgotPasswordModal";

export const AuthScreen: React.FC = () => {
  const navigation = useNavigation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const {signIn} = useAuth();
  const {setUser} = useAuth();
  const {userRecords} = useUserRecords();
  const [forgotModalVisible, setForgotModalVisible] = useState<boolean>(false);

  function handleSign() {
    signIn({username, password})
      .then(res => {
        if (res.error) {
          return;
        }
        getUser()
          .then(async (res) => {
            await SecureStore.setItemAsync("personal-user", JSON.stringify(res.data));
            setUser(res.data);
          });
      });
  }

  function handleForgotModal(visible) {
    setForgotModalVisible(visible);
  }

  return (
    <KeyboardAvoidingView
      style={{flex: 1}}
      behavior={Platform.OS == "ios" ? "padding" : "height"}
    >
      <View style={styles.container}>
        <View style={styles.headerContainer}>
        </View>
        <View style={styles.centerContainer}>
          <Text style={styles.text}>Faça seu login</Text>
          <View style={styles.inputContainer}>
            <Feather name={"mail"} size={24} color={Theme.iconColor} style={styles.inputIcon}/>
            <TextInput
              keyboardAppearance="dark"
              placeholderTextColor="#666360"
              placeholder="E-mail"
              style={styles.inputText}
              value={username}
              keyboardType={"email-address"}
              onChangeText={text => setUsername(text)}
              returnKeyType={"done"}
              autoCapitalize={"none"}
            />
          </View>
          <View style={styles.inputContainer}>
            <Feather name={"lock"} size={24} color={Theme.iconColor} style={styles.inputIcon}/>
            <TextInput
              secureTextEntry
              keyboardAppearance="dark"
              placeholderTextColor="#666360"
              placeholder="Senha"
              style={styles.inputText}
              onChangeText={text => setPassword(text)}
              value={password}
              returnKeyType={"done"}
            />
          </View>
          <Button title={"Login"}
                  style={styles.nextButton}
                  onPress={handleSign}
                  textColor={{color: GREY, fontWeight: "bold"}}/>
          <TouchableOpacity onPress={() => setForgotModalVisible(true)}>
            <Text style={styles.forgotText}>Esqueci minha senha</Text>
          </TouchableOpacity>
        </View>
        <TouchableOpacity style={styles.footerContainer} onPress={() => {
          navigation.navigate("Register");
        }}>
          <Feather name={"log-in"} size={24} color={Theme.iconColor} style={styles.inputIcon}/>
          <Text style={styles.registerText}>Criar uma conta</Text>
        </TouchableOpacity>
      </View>
      <ForgotPasswordModal isModalVisible={forgotModalVisible} handleForgotModal={handleForgotModal}/>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: Theme.scrollViewBackgroundColor,
    justifyContent: "center",
    alignItems: "center",
    height: "100%"
  },
  headerContainer: {
    flex: 3,
    justifyContent: "center"
  },
  centerContainer: {
    flex: 6,
    justifyContent: "flex-start"
  },
  footerContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    flexDirection: "row"
  },
  text: {
    marginHorizontal: 16,
    textAlign: "center",
    fontSize: Theme.textSize.section * 1.2,
    color: Theme.textColor.secondary,
    marginBottom: 16
  },
  inputContainer: {
    flexDirection: "row",
    paddingHorizontal: 16,
    marginTop: 16,
    height: 50,
    width: SCREEN_WIDTH - 64,
    backgroundColor: Theme.card.backgroundColor,
    borderRadius: 8
  },
  inputText: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary,
    width: "100%"
  },
  inputIcon: {
    alignSelf: "center",
    marginRight: 16
  },
  nextButton: {
    marginTop: 16
  },
  forgotText: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary,
    textAlign: "center",
    marginTop: 16
  },
  registerText: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary,
    textAlign: "center"
  }
});
