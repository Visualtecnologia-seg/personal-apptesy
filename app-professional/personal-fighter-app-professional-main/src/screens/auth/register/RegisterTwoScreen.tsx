import React, {useState} from "react";
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View
} from "react-native";
import {GREY, SCREEN_WIDTH, Theme, WHITE1, YELLOW} from "../../../theme";
import {Feather} from "@expo/vector-icons";
import {Button} from "../../../components";

import {useAuth} from "../../../context/AuthContext";
import {useNavigation} from "@react-navigation/native";
import {RadioButton} from "react-native-paper";
import {TextInputMask} from "react-native-masked-text";
import DateTimePicker from "@react-native-community/datetimepicker";

import * as yup from "yup";
import {getUser} from "../../../services/UserService";
import {cpfValidation} from "../../../utils/Validations";
import dayjs from "dayjs";
import {saveUser} from "../../../services/AuthService";
import {Loading} from "../../../components/general/loading";

export const RegisterTwoScreen: React.FC = () => {
  /* Default */
  const [loading, setLoading] = useState<boolean>(false);
  const [showDatePicker, setShowDatePicker] = useState<boolean>(false);
  const [calendarDate, setCalendarDate] = useState<any>();
  const navigation = useNavigation();
  const {signIn} = useAuth();
  /* Notification */
  const [errors, setErrors] = useState<any>({});
  /* Form */
  const {user, setUser} = useAuth();
  const [gender, setGender] = useState<string>("MALE");


  async function handleSignUp() {
    let errors: any = {};
    const userSchema = yup.object().shape({
      name: yup
        .string()
        .min(3, () => (errors.name = "O nome deve ter no mínimo 3 caracteres"))
        .required(() => (errors.name = "Campo obrigatório")),
      surname: yup
        .string()
        .min(3, () => (errors.surname = "O sobrenome deve ter no mínimo 3 caracteres"))
        .required(() => (errors.surname = "Campo obrigatório")),
      phoneNumber: yup
        .string()
        .required(() => (errors.phoneNumber = "Campo obrigatório")),
      birthday: yup
        .string()
        .required(() => (errors.birthday = "Campo obrigatório")),
      cpf: yup.string().required(() => (errors.cpf = "Campo obrigatório"))
    });

    if (calendarDate) {
      user.birthday = dayjs(calendarDate).format("YYYY-MM-DD");
    } else {
      // TODO Analisar
      errors.birthday = "Campo obrigatório";
    }

    let valid = await userSchema.isValid(user);

    // TODO Ainda é necessária essa validação?
    if (user.birthday === "Invalid date" && !errors?.birthday) {
      errors.birthday = "Data inválida";
      valid = false;
    }
    if (!cpfValidation(user?.cpf) && !errors?.cpf) {
      errors.cpf = "Número de CPF inválido";
      valid = false;
    }

    setErrors(errors);

    if (valid) {
      await userSchema.isValid(user);
      const result = Object.entries(errors);
      setErrors(result);

      if (result.length === 0) {
        setLoading(false);
      } else {
        setLoading(false);
      }

      setLoading(true);
      saveUser(user).then(res => {
        if (res.error) {
          setLoading(false);
          return;
        }

        signIn({username: user.username, password: user.password})
          .then(() => {
              getUser().then(res => {
                if (res.error) {
                  setLoading(false);
                }
                setUser(res.data);
                setLoading(true);
              });
            }
          );
      });
    }
  }

  if (loading) {
    return (<Loading/>);
  }

  return (
    <ScrollView contentContainerStyle={{flex: 1}}>
      <KeyboardAvoidingView
        style={styles.main}
        behavior={Platform.OS == "ios" ? "padding" : "height"}
      >
        <Text style={styles.title}>Dados pessoais</Text>
        <View style={styles.inputContainer}>
          <Feather
            name={"user"}
            size={24}
            color={Theme.iconColor}
            style={styles.inputIcon}
          />
          <TextInput
            keyboardAppearance="dark"
            placeholderTextColor="#666360"
            placeholder="Nome"
            returnKeyType={"done"}
            selectionColor={YELLOW}
            style={styles.inputText}
            value={user?.name}
            onChangeText={(text) => {
              setUser({...user, name: text.replace(/\s/g, "")});
            }}
          />
        </View>
        {errors?.name && <Text style={styles.errors}>{errors?.name}</Text>}
        <View style={styles.inputContainer}>
          <Feather
            name={"user"}
            size={24}
            color={Theme.iconColor}
            style={styles.inputIcon}
          />
          <TextInput
            keyboardAppearance="dark"
            placeholderTextColor="#666360"
            placeholder="Sobrenome"
            returnKeyType={"done"}
            selectionColor={YELLOW}
            style={styles.inputText}
            value={user?.surname}
            onChangeText={(text) => {
              setUser({...user, surname: text});
            }}
          />
        </View>
        {errors?.surname && (
          <Text style={styles.errors}>{errors?.surname}</Text>
        )}
        <View style={styles.inputContainer}>
          <Feather
            name={"credit-card"}
            size={24}
            color={Theme.iconColor}
            style={styles.inputIcon}
          />
          <TextInputMask
            type={"cpf"}
            keyboardAppearance="dark"
            placeholderTextColor="#666360"
            placeholder="CPF"
            returnKeyType={"done"}
            selectionColor={YELLOW}
            style={styles.inputText}
            value={user?.cpf}
            onChangeText={(text) => {
              setUser({...user, cpf: text});
            }}
          />
        </View>
        {errors?.cpf && <Text style={styles.errors}>{errors?.cpf}</Text>}
        <View style={styles.inputContainer}>
          <Feather
            name={"phone"}
            size={24}
            color={Theme.iconColor}
            style={styles.inputIcon}
          />
          <TextInputMask
            type={"cel-phone"}
            options={{
              maskType: "BRL",
              withDDD: true
            }}
            keyboardAppearance="dark"
            placeholderTextColor="#666360"
            placeholder="Telefone"
            returnKeyType={"done"}
            selectionColor={YELLOW}
            style={styles.inputText}
            value={user?.phoneNumber}
            onChangeText={(text) => {
              setUser({...user, phoneNumber: text});
            }}
          />
        </View>
        {errors?.phoneNumber && (
          <Text style={styles.errors}>{errors?.phoneNumber}</Text>
        )}
        <TouchableOpacity
          style={[styles.inputContainer, {alignItems: "center"}]}
          onPress={() => {
            setShowDatePicker(!showDatePicker);
          }}
        >
          <Feather
            name="calendar"
            size={24}
            style={styles.inputIcon}
            color={Theme.iconColor}
          />
          <Text style={user.birthday ? styles.inputText : styles.inputPlaceholder}>
            {user.birthday ? user.birthday : "Data de nascimento"}
          </Text>
        </TouchableOpacity>
        <View style={styles.inputContainerGender}>
          <View style={{flex: 1}}>
            <View
              style={{
                marginTop: 5,
                flexDirection: "row",
                alignItems: "center"
              }}
            >
              <RadioButton.Android
                value={gender}
                status={user.gender === "MALE" ? "checked" : "unchecked"}
                onPress={() => {
                  setUser({...user, gender: "MALE"});
                  setGender("MALE");
                }}
                color={YELLOW}
              />
              <Text style={styles.inputText}>Masculino</Text>
            </View>
          </View>
          <View style={{flex: 1}}>
            <View
              style={{
                marginTop: 5,
                flexDirection: "row",
                alignItems: "center"
              }}
            >
              <RadioButton.Android
                value={gender}
                status={user.gender === "FEMALE" ? "checked" : "unchecked"}
                onPress={() => {
                  setUser({...user, gender: "FEMALE"});
                  setGender("FEMALE");
                }}
                color={YELLOW}
              />
              <Text style={styles.inputText}>Feminino</Text>
            </View>
          </View>
        </View>
        <Button
          title={"Cadastrar"}
          onPress={handleSignUp}
          textColor={{color: GREY, fontWeight: "bold"}}
          style={{marginTop: 10, width: SCREEN_WIDTH - 64}}
        />
      </KeyboardAvoidingView>
      {showDatePicker && (
        <View>
          {Platform.OS === "ios" && (
            <View
              style={{
                width: SCREEN_WIDTH,
                justifyContent: "center",
                alignItems: "flex-end",
                paddingRight: 10,
                height: 45,
                backgroundColor: WHITE1
              }}
            >
              <TouchableOpacity onPress={() => setShowDatePicker(false)}>
                <Text>Selecionar</Text>
              </TouchableOpacity>
            </View>
          )}
          <View style={{backgroundColor: "white"}}>
            {/* TODO Analisara o bug de data quando há atualização de tela (validação, retorno de tela e cancelar no calendário) */}
            <DateTimePicker
              value={calendarDate ? calendarDate : new Date("2013-01-01")}
              mode={"date"}
              locale={"pt"}
              display="spinner"
              style={{backgroundColor: "white"}}
              maximumDate={new Date()}
              onTouchCancel={() => setCalendarDate(null)}
              onChange={(event, selectedDate) => {
                if (Platform.OS === "android") {
                  setShowDatePicker(false);
                }
                setCalendarDate(selectedDate);
                setUser({
                  ...user,
                  birthday: dayjs(selectedDate).format("DD/MM/YYYY")
                });
              }}
            />
          </View>
        </View>
      )}
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
  inputContainerGender: {
    flexDirection: "row",
    paddingHorizontal: 16,
    marginTop: 16,
    height: 50,
    width: SCREEN_WIDTH - 64,
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
  inputPlaceholder: {
    color: "#666360",
    width: "100%"
  },
  main: {
    flex: 1,
    width: "100%",
    paddingHorizontal: 16,
    paddingBottom: 16,
    justifyContent: "center",
    alignItems: "center"
  },
  title: {
    fontSize: 24,
    color: Theme.textColor.white,
    fontFamily: "MontserratSemiBold",
    textAlign: "center",
    marginBottom: 6
  }
});
