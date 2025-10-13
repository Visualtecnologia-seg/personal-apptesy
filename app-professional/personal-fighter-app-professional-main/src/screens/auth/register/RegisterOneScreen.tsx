import React, {useState} from "react";
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";
import {GREY, SCREEN_WIDTH, Theme, YELLOW} from "../../../theme";
import {Feather} from "@expo/vector-icons";
import {Button} from "../../../components";

import {useNavigation} from "@react-navigation/native";
import {UserModel} from "../../../model";

import * as yup from "yup";
import {NavigationNames} from "../../../navigations/NavigationNames";
import {checkIfExists} from "../../../services/AuthService";
import {useAuth} from "../../../context/AuthContext";


export const RegisterOneScreen: React.FC = () => {
  /* Default */
  const [loading, setLoading] = useState<boolean>(false);
  const navigation = useNavigation();
  /* Notification */
  const [errors, setErrors] = useState<any>({});
  /* Form */
  const {setUser} = useAuth();
  const [data, setData] = useState<UserModel>({gender: "MALE"} as UserModel);

  async function handleNext() {
    let errors: any = {};
    const userSchema = yup.object().shape({
      email: yup
        .string()
        .email(() => (errors.email = "Email inválido"))
        .required(() => (errors.email = "Campo obrigatório")),
      password: yup
        .string()
        .required(() => (errors.password = "Campo obrigatório")),
      confirmPassword: yup
        .string()
        .oneOf(
          [yup.ref("password")],
          () => (errors.confirmPassword = "As senhas não são iguais.")
        )
        .required(() => (errors.confirmPassword = "Campo obrigatório"))
    });

    let valid = await userSchema.isValid(data);

    setErrors(errors);

    if (valid) {
      data.username = data.email;
      await userSchema.isValid(data);
      const result = Object.entries(errors);
      setErrors(result);

      if (result.length === 0) {
        const existEmail: any = await checkIfExists(data.email);
        if (existEmail.data) {
          setLoading(false);
          navigation.goBack();
          Alert.alert(
            "",
            "Email já cadastrado, por favor faça o login!"
          );
          return;
        }
        setUser(data);
        setLoading(false);
        navigation.navigate(NavigationNames.RegisterScreenTwo);
      } else {
        setLoading(false);
      }
    }
  }

  return (
    <ScrollView contentContainerStyle={{flex: 1}}>
      <KeyboardAvoidingView
        style={styles.main}
        behavior={Platform.OS == "ios" ? "padding" : "height"}
      >
        <Text style={styles.title}>Dados cadastrais</Text>
        <View style={styles.inputContainer}>
          <Feather
            name={"mail"}
            size={24}
            color={Theme.iconColor}
            style={styles.inputIcon}
          />
          <TextInput
            keyboardAppearance="dark"
            placeholderTextColor="#666360"
            placeholder="E-mail"
            returnKeyType={"done"}
            selectionColor={YELLOW}
            style={styles.inputText}
            value={data?.email}
            keyboardType="email-address"
            autoCapitalize="none"
            onChangeText={(text) => {
              setData({...data, email: text.toLowerCase()});
            }}
          />
        </View>
        {errors?.email && (<Text style={styles.errors}>{errors?.email}</Text>)}
        <View style={styles.inputContainer}>
          <Feather
            name={"lock"}
            size={24}
            color={Theme.iconColor}
            style={styles.inputIcon}
          />
          <TextInput
            keyboardAppearance="dark"
            secureTextEntry={true}
            placeholderTextColor="#666360"
            placeholder="Senha"
            returnKeyType={"done"}
            style={styles.inputText}
            selectionColor={YELLOW}
            onChangeText={(text) => {
              setData({...data, password: text});
            }}
          />
        </View>
        {errors?.password && (<Text style={styles.errors}>{errors?.password}</Text>)}
        <View style={styles.inputContainer}>
          <Feather
            name={"lock"}
            size={24}
            color={Theme.iconColor}
            style={styles.inputIcon}
          />
          <TextInput
            keyboardAppearance="dark"
            placeholderTextColor="#666360"
            placeholder="Confirmação de senha"
            returnKeyType={"done"}
            secureTextEntry={true}
            style={styles.inputText}
            selectionColor={YELLOW}
            onChangeText={(text) => {
              setData({...data, confirmPassword: text});
            }}
          />
        </View>
        {errors?.confirmPassword && (<Text style={styles.errors}>{errors?.confirmPassword}</Text>)}
        <Button
          title={"Próximo"}
          onPress={handleNext}
          textColor={{color: GREY, fontWeight: "bold"}}
          style={{marginTop: 16, width: SCREEN_WIDTH - 64}}
        />
      </KeyboardAvoidingView>
      <View style={styles.footer}>
        <TouchableOpacity
          style={styles.footerContainer}
          onPress={() => navigation.goBack()}
        >
          <Feather
            name={"arrow-left-circle"}
            size={26}
            color={Theme.iconColor}
            style={styles.inputIcon}
          />
          <Text style={styles.registerText}>Voltar</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  scrollview: {
    backgroundColor: Theme.scrollViewBackgroundColor,
    padding: 16,
    height: "100%"
  },
  main: {
    flex: 1,
    width: "100%",
    paddingHorizontal: 16,
    paddingBottom: 16,
    justifyContent: "center",
    alignItems: "center"
  },
  footer: {
    paddingBottom: 16,
    justifyContent: "center"
  },
  footerContainer: {
    marginTop: 16,
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
  datePicker: {
    height: 45,
    alignItems: "flex-start",
    backgroundColor: Theme.card.backgroundColor,
    borderRadius: 4,
    marginBottom: 10
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
  registerText: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary,
    textAlign: "center"
  },
  errors: {
    color: Theme.textColor.yellow,
    fontSize: Theme.textSize.secondary,
    marginTop: 2
  },
  title: {
    fontSize: 24,
    color: Theme.textColor.white,
    fontFamily: "MontserratSemiBold",
    textAlign: "center",
    marginBottom: 6
  }
});
