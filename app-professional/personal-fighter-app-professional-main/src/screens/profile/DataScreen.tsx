import React, {useState} from "react";
import {Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TextInput, View} from "react-native";
import {Theme, WHITE3, YELLOW} from "../../theme";
import {Button} from "../../components";
import {useAuth} from "../../context/AuthContext";
import {Loading} from "../../components/general/loading";
import {useNavigation} from "@react-navigation/native";
import {updateUser} from "../../services/UserService";

type TProps = {};

export const DataScreen: React.FC<TProps> = props => {
  const {user, setUser} = useAuth();
  const navigation = useNavigation();
  const [loading, setLoading] = useState<boolean>(false);

  function onSave() {
    updateUser(user).then(res => {
      if (res !== null) {
        setUser(res);
        navigation.goBack();
      } else {
        Alert.alert("Erro!",
          "Não foi possível atualizar seus dados.");
      }
    });
  }

  if (loading) {
    return (<Loading/>);
  }

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS == "ios" ? "padding" : "height"}
    >
      <ScrollView
        contentContainerStyle={styles.scrollview}
        showsVerticalScrollIndicator={false}
      >
        <View>
          <Text style={styles.title}>Referência para favorito</Text>
          <TextInput
            style={[styles.textInput, {color: WHITE3}]}
            placeholderTextColor={WHITE3}
            selectionColor={YELLOW}
            value={user?.professional?.reference}
            editable={false}
          />
        </View>
        <View>
          <Text style={styles.title}>CPF</Text>
          <TextInput
            style={[styles.textInput, {color: WHITE3}]}
            placeholder={"CPF"}
            placeholderTextColor={WHITE3}
            selectionColor={YELLOW}
            value={user.cpf}
            editable={false}
          />
        </View>
        <View>
          <Text style={styles.title}>E-mail</Text>
          <TextInput
            style={[styles.textInput, {color: WHITE3}]}
            placeholder={"E-mail"}
            placeholderTextColor={WHITE3}
            selectionColor={YELLOW}
            returnKeyType={"done"}
            value={user.email}
            editable={false}
          />
        </View>
        <View>
          <Text style={styles.title}>Nome</Text>
          <TextInput
            style={styles.textInput}
            placeholder={"Nome"}
            returnKeyType={"done"}
            placeholderTextColor={WHITE3}
            selectionColor={YELLOW}
            value={user.name}
            onChangeText={(text) => {
              setUser({...user, name: text.replace(/\s/g, "")});
            }}
          />
        </View>
        <View>
          <Text style={styles.title}>Sobrenome</Text>
          <TextInput
            style={styles.textInput}
            placeholder={"Sobrenome"}
            placeholderTextColor={WHITE3}
            selectionColor={YELLOW}
            returnKeyType={"done"}
            value={user.surname}
            onChangeText={(text) => {
              setUser({...user, surname: text});
            }}
          />
        </View>
        <View style={styles.buttonView}>
          <Button title={"Salvar"}
                  style={{backgroundColor: Theme.nextButton.colors.primary}}
                  textColor={{color: Theme.textColor.dark}}
                  onPress={() => onSave()}/>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  scrollview: {
    height: "100%",
    backgroundColor: Theme.scrollViewBackgroundColor,
    padding: 16
  },
  buttonView: {
    flexDirection: "row",
    justifyContent: "flex-end",
    marginTop: 10
  },
  textInput: {
    backgroundColor: Theme.card.backgroundColor,
    height: 45,
    borderRadius: 4,
    marginBottom: 10,
    paddingLeft: 16,
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary
  },
  textAreaInput: {
    textAlignVertical: "top",
    backgroundColor: Theme.card.backgroundColor,
    height: 100,
    borderRadius: 4,
    marginBottom: 10,
    paddingTop: 6,
    paddingLeft: 16,
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary
  },
  row: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center"
  },
  col: {
    flex: 1
  },
  title: {
    color: Theme.textColor.secondary,
    fontSize: Theme.textSize.secondary,
    marginBottom: 5,
    marginLeft: 2
  },
  select: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary
  }
});
