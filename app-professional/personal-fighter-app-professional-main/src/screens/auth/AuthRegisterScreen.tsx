import React, {useEffect, useState} from "react";
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
import {GREY, Theme, YELLOW} from "../../theme";
import {Feather} from "@expo/vector-icons";
import {Button} from "../../components";
import {useAuth} from "../../context/AuthContext";
import {useNavigation} from "@react-navigation/native";
import {UserModel} from "../../model";
import {Checkbox} from "react-native-paper";
import * as yup from "yup";
import {getUser, saveUser, saveUserRecords} from "../../services/UserService";
import {useUserRecords} from "../../context/UserRecordsContext";
import {ProductModel} from "../../model/ProductModel";
import {getProducts} from "../../services/ProductsService";
import {Loading} from "../../components/general/loading";

export const AuthRegisterScreen: React.FC = () => {
  const navigation = useNavigation();
  const {user, setUser} = useAuth();
  const {signIn} = useAuth();
  const [data, setData] = useState<UserModel>({
    gender: "MALE",
    bankData: {
      accountType: "CURRENT_ACCOUNT"
    },
    products: []
  } as UserModel);
  const [errors, setErrors] = useState<any>({});
  const {userRecords} = useUserRecords();
  const [bankCode, setBankCode] = useState<string>("");
  const [products, setProducts] = useState<ProductModel[]>([]);
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    getProducts().then((response) => {
      setProducts(response);
      setData({...user, products: []});
    });
  }, []);

  async function handleSignUp() {
    let errors: any = {};
    const userSchema = yup.object().shape({
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

    if (data.products.length === 0) {
      errors.products = "Campo obrigatório";
    }
    setErrors(errors);

    if (valid) {
      setLoading(true);

      setUser(data);

      saveUser(user).then((res) => {
        if (res.id !== undefined) {
          signIn({username: data.username, password: data.password}).then(
            (res) => {
              getUser(res.jwtToken).then((user) => {
                setUser(user);
              });
              saveUserRecords(userRecords, res.jwtToken).then(() => {
              });
              setLoading(false);
            }
          );
        } else {
          Alert.alert("Erro!", "Usuário não cadastrado.");
        }
      });
    }
  }

  if (loading) {
    return <Loading/>;
  }

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : "height"}
      keyboardVerticalOffset={Platform.OS === "ios" ? 64 : 0}
    >
      <ScrollView
        contentContainerStyle={styles.scrollview}
        showsVerticalScrollIndicator={false}
      >
        <View>
          <Text style={styles.sectionTitle}>Crie sua conta</Text>
        </View>
        <View style={[styles.input, {marginTop: 10}]}>
          <Text style={styles.title}>Senha</Text>
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
              placeholder="Digite sua senha..."
              style={styles.inputText}
              selectionColor={YELLOW}
              onChangeText={(text) => {
                setData({...data, password: text});
              }}
            />
          </View>
          {errors?.password && (
            <Text style={styles.errors}>* {errors?.password}</Text>
          )}
        </View>

        <View style={styles.input}>
          <Text style={styles.title}>Confirmação de senha</Text>
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
              placeholder="Repita a senha..."
              secureTextEntry={true}
              style={styles.inputText}
              selectionColor={YELLOW}
              onChangeText={(text) => {
                setData({...data, confirmPassword: text});
              }}
            />
          </View>
          {errors?.confirmPassword && (
            <Text style={styles.errors}>* {errors?.confirmPassword}</Text>
          )}
        </View>
        <View style={[styles.input]}>
          <Text style={styles.title}>Modalidades</Text>
          <View style={{flexDirection: "row", flexWrap: "wrap"}}>
            {products.map((product) => (
              <View
                style={{
                  flexDirection: "row",
                  width: "50%",
                  alignItems: "center"
                }}
              >
                <Checkbox
                  status={
                    data.products.includes(product) ? "checked" : "unchecked"
                  }
                  onPress={() => {
                    let products = [...data.products];
                    if (products.includes(product)) {
                      products = products.filter((value) => {
                        if (product.id !== value.id) {
                          return value;
                        }
                      });
                    } else {
                      products.push(product);
                    }
                    setData({...data, products});
                  }}
                  color={YELLOW}
                />
                <Text style={styles.checkboxText}>{product.name}</Text>
              </View>
            ))}
          </View>
        </View>
        <View style={{marginTop: 30}}>
          <Button
            title={"Cadastrar"}
            onPress={handleSignUp}
            textColor={{color: GREY, fontWeight: "bold"}}
          />
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
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  scrollview: {
    backgroundColor: Theme.scrollViewBackgroundColor,
    height: "100%",
    padding: 16
  },
  footerContainer: {
    marginTop: 16,
    justifyContent: "center",
    alignItems: "center",
    flexDirection: "row"
  },
  sectionTitle: {
    marginHorizontal: 16,
    textAlign: "center",
    fontSize: Theme.textSize.section * 1.2,
    color: Theme.textColor.secondary,
    marginBottom: 16
  },
  input: {
    marginBottom: 5
  },
  inputContainer: {
    flexDirection: "row",
    paddingHorizontal: 16,
    marginBottom: 10,
    height: 45,

    backgroundColor: Theme.card.backgroundColor,
    borderRadius: 4
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
  checkboxText: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary
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
  title: {
    color: Theme.textColor.secondary,
    fontSize: Theme.textSize.secondary,
    marginBottom: 5,
    marginLeft: 2
  },
  errors: {
    color: Theme.textColor.yellow,
    fontSize: Theme.textSize.secondary,
    marginBottom: 5
  }
});
