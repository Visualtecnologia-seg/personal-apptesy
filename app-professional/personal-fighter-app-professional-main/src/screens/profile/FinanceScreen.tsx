import React, {useEffect, useState} from "react";
import {Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TextInput, View} from "react-native";
import {Theme, YELLOW} from "../../theme";
import {Button} from "../../components";
import {Loading} from "../../components/general/loading";
import {useNavigation} from "@react-navigation/native";
import {TextInputMask} from "react-native-masked-text";
import {getBankData, saveBankData} from "../../services/FinanceService";
import {useAuth} from "../../context/AuthContext";
import {BankDataModel} from "../../model/BankDataModel";

type TProps = {};

export const FinanceScreen: React.FC<TProps> = props => {
  const navigation = useNavigation();
  const {user} = useAuth();
  const [loading, setLoading] = useState<boolean>(true);
  const [data, setData] = useState<BankDataModel>({} as BankDataModel);

  useEffect(() => {
    getBankData(user.id).then(res => {
      if (res.error) {
        navigation.goBack();
      } else {
        setData(res.data);
        setLoading(false);
      }
    });
  }, []);

  function onSave() {
    data.professional.id = user.id;
    saveBankData(data).then(res => {
      if (res.error) {
        Alert.alert("Erro!",
          "Não foi possível atualizar seus dados.");
      } else {
        navigation.goBack();
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
          <Text style={styles.title}>Chave PIX (CPF)</Text>
          <TextInputMask
            type={"cpf"}
            keyboardAppearance="dark"
            placeholderTextColor="#666360"
            placeholder="CPF"
            returnKeyType={"done"}
            selectionColor={YELLOW}
            style={styles.textInput}
            value={data?.pixCpf}
            onChangeText={(text) => {
              setData({...data, pixCpf: text});
            }}
          />
        </View>
        <View>
          <Text style={styles.title}>Chave PIX (E-mail)</Text>
          <TextInput
            keyboardAppearance="dark"
            placeholderTextColor="#666360"
            placeholder="E-mail"
            returnKeyType={"done"}
            selectionColor={YELLOW}
            style={styles.textInput}
            value={data?.pixEmail}
            keyboardType="email-address"
            autoCapitalize="none"
            onChangeText={(text) => {
              setData({...data, pixEmail: text.toLowerCase()});
            }}
          />
        </View>
        <View>
          <Text style={styles.title}>Chave PIX (Telefone)</Text>
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
            style={styles.textInput}
            value={data?.pixPhoneNumber}
            onChangeText={(text) => {
              setData({...data, pixPhoneNumber: text});
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
