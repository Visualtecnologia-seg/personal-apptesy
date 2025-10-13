import React, {useEffect, useState} from "react";
import {Alert, FlatList, ScrollView, StyleSheet, Text, TouchableOpacity, View} from "react-native";
import {Avatar} from "../../components";
import {DARKGREY, Theme} from "../../theme";
import {Loading} from "../../components/general/loading";
import {useAuth} from "../../context/AuthContext";
import {useIsFocused, useNavigation} from "@react-navigation/native";
import {Feather} from "@expo/vector-icons";
import * as ImageManipulator from "expo-image-manipulator";
import * as ImagePicker from "expo-image-picker";
import {ProfileListItem} from "./component/ProfileListItem";
import {NavigationNames} from "../../navigations/NavigationNames";
import {postAvatar} from "../../services/PhotoService";
import {getFinance} from "../../services/FinanceService";
import {FinanceModel} from "../../model/FinanceModel";
import {getUser} from "../../services/UserService";

type TProps = {};

const items = [
  {id: "1", icon: "user", title: "Meus dados", navigation: NavigationNames.DataScreen},

  {id: "2", icon: "dollar-sign", title: "Chaves PIX", navigation: NavigationNames.FinanceScreen},
  {id: "3", icon: "align-justify", title: "Minhas modalidades", navigation: NavigationNames.ProductScreen},
  {id: "4", icon: "award", title: "Meu currículo", navigation: NavigationNames.ResumeScreen},
  {id: "5", icon: "camera", title: "Minhas fotos", navigation: NavigationNames.PhotoAlbumScreen},
  {id: "6", icon: "lock", title: "Alterar senha", navigation: NavigationNames.ChangePasswordScreen},
  // {id: "6", icon: "message-square", title: "Fale conosco", navigation: NavigationNames.ProfileScreen},
  {id: "7", icon: "shield", title: "Política de privacidade", navigation: NavigationNames.PrivacyPolicy},
  {id: "8", icon: "book", title: "Termos de uso", navigation: NavigationNames.TermsOfService}
];

export const ProfileScreen: React.FC<TProps> = props => {
  /* Default */
  const navigation = useNavigation();
  const {token, signOut} = useAuth();
  const isFocused = useIsFocused();
  const [loading, setLoading] = useState<boolean>(true);
  const {user, setUser} = useAuth();
  /* Finance */
  const [finance, setFinance] = useState<FinanceModel>(undefined);

  useEffect(() => {
    if (isFocused) {
      getFinance(user.id).then(res => {
        setFinance(res.data);
        setLoading(false);
        if (user.avatarUrl === null) {
          Alert.alert("Atenção", "Seu perfil de profissional somente ficará ativo quando inserir uma foto no perfil!");
        }
      });
    }
  }, [isFocused]);

  async function pickImage() {
    let result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: false,
      aspect: [4, 3],
      quality: 1
    });
    if (result.cancelled === false) {
      setLoading(true);
      const acceptedTypes = ["jpg", "png"];
      let typeImage = result.uri.substring(result.uri.lastIndexOf(".") + 1);
      if (acceptedTypes.includes(typeImage)) {
        let imageResized = await ImageManipulator.manipulateAsync(result.uri, [{
          resize: {
            height: 320
          }
        }]);
        const formData = new FormData();
        formData.append("file", {
          // @ts-ignore
          uri: imageResized.uri,
          name: "avatar.jpg",
          type: "image/jpeg"
        });
        postAvatar(formData, user.id).then(res => {
          getUser().then(res => {
            setUser(res.data);
            setLoading(false);
          });

        });

      } else {
        Alert.alert("", "o tipo da imagem selecionada não é suportada");
      }
    }
  }

  function formatCurrency(value) {
    if (value === 0) {
      return "R$ 0,00";
    }
    let isNegative = value < 0;
    value = value?.toString().replace(".", ",").replace("-", "");
    isNegative ? value = "- R$ " + value + 0 : value = "R$ " + value + 0;
    return value;
  }

  if (loading) {
    return (<Loading/>);
  }

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.scrollContainer}
    >
      <View style={styles.userView}>
        <TouchableOpacity onPress={() => pickImage()}>
          <Avatar
            imageStyle={styles.userImage}
            source={{
              uri:
              user.avatarUrl
            }}
          />
        </TouchableOpacity>
        <View style={{justifyContent: "center"}}>
          <Text style={styles.userName}>{user?.name}</Text>
          <Text style={styles.userBalance}>Saldo: {formatCurrency(finance?.professionalBalance)}</Text>
        </View>
      </View>
      <FlatList
        data={items}
        scrollEnabled={true}
        keyExtractor={item => item.id}
        renderItem={({item}) => (
          <ProfileListItem
            icon={item.icon}
            title={item.title}
            navigation={() => navigation.navigate(item.navigation)}/>
        )}
      />
      <TouchableOpacity style={styles.footerContainer} onPress={signOut}>
        <Feather name={"log-out"} size={24} color={Theme.iconColor} style={styles.inputIcon}/>
        <Text style={styles.registerText}>Logout</Text>
      </TouchableOpacity>
    </ScrollView>
  );
};
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Theme.scrollViewBackgroundColor,
    paddingVertical: 16
  },
  scrollContainer: {
    backgroundColor: Theme.scrollViewBackgroundColor
  },
  userView: {
    flexDirection: "row",
    marginHorizontal: 16,
    marginBottom: 10,
    backgroundColor: Theme.card.backgroundColor,
    paddingVertical: 10,
    paddingHorizontal: 16,
    shadowColor: DARKGREY,
    shadowOffset: {
      width: 0,
      height: 2
    },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 1,
    borderRadius: 8
  },
  userImage: {
    width: 76,
    height: 76,
    borderRadius: 36,
    borderWidth: 3,
    borderColor: Theme.imageBorderColor,
    justifyContent: "center",
    alignItems: "center"
  },
  footerContainer: {
    marginTop: 10,
    marginBottom: 50,
    justifyContent: "center",
    alignItems: "center",
    flexDirection: "row"
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
  userName: {
    fontSize: Theme.textSize.section,
    color: Theme.textColor.primary,
    marginLeft: 10
  },
  userBalance: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary,
    marginLeft: 10
  }

});
