import React, {useEffect, useState} from "react";
import {FlatList, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TextInput, View} from "react-native";
import {Theme, WHITE3, YELLOW} from "../../theme";
import {useAuth} from "../../context/AuthContext";
import {ResumeListItem} from "./component/ResumeListItem";
import {UserModel} from "../../model";
import {AddListItem} from "./component/AddListItem";
import {updateUser} from "../../services/UserService";
import {Loading} from "../../components/general/loading";
import ResumeModal from "./component/ResumeModal";

type TProps = {};

export const ResumeScreen: React.FC<TProps> = props => {
  /* Default */
  const {user, setUser} = useAuth();
  const [loading, setLoading] = useState(true);
  const [modalVisible, setModalVisible] = useState<boolean>(false);
  /* User */
  const [userData, setUserData] = useState<UserModel>();
  /* Resume */
  const [resume, setResume] = useState<any[]>([]);
  /* About */
  const [about, setAbout] = useState<string>();

  useEffect(() => {
    if (user.professional.resume) {
      setResume(JSON.parse(user.professional.resume));
    }
    if (user.professional.about) {
      setAbout(user.professional.about);
    }
    setLoading(false);
  }, [user]);

  function saveResume(result) {
    setLoading(true);
    updateUser({...user, professional: {...user.professional, resume: JSON.stringify(result)}})
      .then(res => {
        setUser(res.data);
        setLoading(false);
      });
  }

  function saveAbout() {
    setLoading(true);
    updateUser({...user, professional: {...user.professional, about: about}})
      .then(res => {
        setLoading(false);
        setUser(res.data);
      });
  }

  function addResumeItem(place, league) {
    setModalVisible(false);
    let id = 1;
    if (resume[resume.length - 1]?.id) {
      id = resume[resume.length - 1].id + 1;
    }
    const result = [...resume, {id: id, place: place, league: league}];
    saveResume(result);
  }

  function removeResumeItem(id) {
    const result = resume.filter(r => r.id !== id);
    saveResume(result);
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
        showsVerticalScrollIndicator={true}
        scrollEnabled={true}
      >
        <View>
          <Text style={styles.title}>Sobre</Text>
          <TextInput
            style={[styles.textAreaInput]}
            placeholder={"Escreva algo sobre você e sua carreira..."}
            placeholderTextColor={WHITE3}
            multiline
            selectionColor={YELLOW}
            returnKeyType={"done"}
            value={about}
            maxLength={255}
            onChangeText={(text) => {
              setAbout(text);
            }}
            onBlur={() => saveAbout()}
          />
        </View>
        <View>
          <View>
            <Text style={styles.title}>Currículo</Text>
          </View>
          <FlatList
            data={resume}
            numColumns={1}
            keyExtractor={item => item?.id}
            renderItem={({item, index}) => (
              <ResumeListItem
                place={item?.place}
                text={item?.league}
                onPress={() => removeResumeItem(item?.id)}/>
            )}
          />
        </View>
        <AddListItem onPress={() => setModalVisible(true)}/>
        <View>
          <ResumeModal visible={modalVisible} onConfirm={addResumeItem} onCancel={() => setModalVisible(false)}/>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  scrollview: {
    backgroundColor: Theme.scrollViewBackgroundColor,
    padding: 16,
    height: "100%"
  },
  buttonView: {
    flexDirection: "row",
    justifyContent: "flex-end",
    marginTop: 20
  },
  plusView: {
    flexDirection: "row",
    justifyContent: "flex-end",
    alignItems: "center",
    marginLeft: 0,
    marginVertical: 5
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
    fontSize: Theme.textSize.primary,
    marginBottom: 5,
    marginRight: 10,
    flex: 1
  },
  select: {
    fontSize: Theme.textSize.primary,
    color: Theme.textColor.primary
  }
});
