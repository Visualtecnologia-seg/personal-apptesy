import React, {useState} from "react";
import {Alert, ScrollView, StyleSheet, Text, TextInput, View} from "react-native";

import {useNavigation} from "@react-navigation/native";
import {Feather} from "@expo/vector-icons";
import {UserModel} from "../../model";

import * as yup from "yup";
import {Loading} from "../../components/general/loading";
import {GREY, Theme, YELLOW} from "../../theme";
import {Button} from "../../components";
import {updateUserPassword} from "../../services/UserService";
import {themeStyles} from "../../config/theme/Styles";

interface Props {
}

export const ChangePasswordScreen: React.FC<Props> = props => {
  /* Default */
  const navigation = useNavigation();
  const [loading, setLoading] = useState<boolean>(false);
  /* Notification */
  const [error, setError] = useState<any>({});
  /* Password */
  const [data, setData] = useState<UserModel>({} as UserModel);

  async function save() {
    setLoading(true);
    const errors: any = {};
    const userSchema = yup.object().shape({
      password: yup
        .string()
        .required(() => errors.password = "O campo de senha é obrigatório"),
      confirmPassword: yup
        .string()
        .oneOf(
          [yup.ref("password")],
          () => errors.confirmPassword = "As senhas não conferem"
        )
        .required(() => errors.confirmPassword = "O campo de confirmação de senha é obrigatório")
    });

    let valid = await userSchema.isValid(data);
    setError(errors);

    if (valid) {
      updateUserPassword({password: data.password, newPassword: data.confirmPassword})
        .then(res => {
          if (res.error) {
            setLoading(false);
          } else {
            setLoading(false);
            Alert.alert("", "Senha atualizada com sucesso!");
            navigation.goBack();
          }
        });
    } else {
      setLoading(false);
    }
  }

  if (loading) {
    return (<Loading/>);
  }

  return (
    <ScrollView
      contentContainerStyle={themeStyles.text}
    >
      <View style={styles.inputContainer}>
        <Feather
          name={"lock"}
          size={24}
          color={Theme.iconColor}
          style={styles.inputIcon}
        />
        <TextInput
          secureTextEntry
          placeholderTextColor={Theme.textColor.placeholder}
          placeholder="Nova senha"
          style={styles.textInput}
          onChangeText={text => {
            setData({...data, password: text});
          }}
          value={data.password}
          returnKeyType="done"
        />

      </View>
      {error?.password && (<Text style={styles.errors}>{error?.password}</Text>)}
      <View style={styles.inputContainer}>
        <Feather
          name={"lock"}
          size={24}
          color={Theme.iconColor}
          style={styles.inputIcon}
        />
        <TextInput
          secureTextEntry
          placeholderTextColor={Theme.textColor.placeholder}
          placeholder="Confirmar senha"
          selectionColor={YELLOW}
          style={styles.textInput}
          onChangeText={text => {
            setData({...data, confirmPassword: text});
          }}
          value={data.confirmPassword}
          returnKeyType="done"
        />
      </View>
      {error?.confirmPassword && (<Text style={styles.errors}>{error?.confirmPassword}</Text>)}
      <Button
        title="Salvar"
        textColor={{color: GREY, fontWeight: "bold"}}
        style={{marginTop: 16}}
        onPress={async () => {
          await save();
        }}
      />
    </ScrollView>
  );
};
const styles = StyleSheet.create({
  inputContainer: {
    flexDirection: "row",
    paddingHorizontal: 16,
    marginTop: 16,
    height: 50,
    backgroundColor: Theme.card.backgroundColor,
    borderRadius: 8
  },
  inputIcon: {
    alignSelf: "center",
    marginRight: 16
  },
  textInput: {
    backgroundColor: Theme.card.backgroundColor,
    borderRadius: 4,
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary
  },
  errors: {
    color: Theme.textColor.yellow,
    fontSize: Theme.textSize.secondary,
    marginTop: 2
  },
  button: {
    marginTop: 16
  }
});
