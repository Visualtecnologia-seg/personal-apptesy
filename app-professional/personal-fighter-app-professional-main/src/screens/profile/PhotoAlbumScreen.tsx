import React, {useEffect, useState} from "react";
import {Alert, FlatList, ScrollView, StyleSheet, View} from "react-native";
import {Theme} from "../../theme";
import {Button, ProfessionalPhotoGrid} from "../../components";
import {useAuth} from "../../context/AuthContext";
import {Loading} from "../../components/general/loading";
import {useIsFocused, useNavigation} from "@react-navigation/native";
import {getProfessionalPhotos, savePhoto} from "../../services/PhotoService";
import {PhotoModel} from "../../model";
import * as ImagePicker from "expo-image-picker";
import * as ImageManipulator from "expo-image-manipulator";

type TProps = {};

export const PhotoAlbumScreen: React.FC<TProps> = props => {
  /* Default */
  const isFocused = useIsFocused();
  const {user} = useAuth();
  const [loading, setLoading] = useState(true);
  const navigation = useNavigation();
  /* Photos */
  const [photos, setPhotos] = useState<PhotoModel[]>([]);

  useEffect(() => {
    if (isFocused) {
      loadProfessionalPhotos();
    }
  }, [isFocused]);

  function loadProfessionalPhotos() {
    setLoading(true);
    getProfessionalPhotos(user.id).then(res => {
      if (res.error) {
        setLoading(false);
      } else {
        setPhotos(res.data);
        setLoading(false);
      }
    });
  }

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
            width: 800
          }
        }]);
        const formData = new FormData();
        formData.append("file", {
          // @ts-ignore
          uri: imageResized.uri,
          name: "avatar.jpg",
          type: "image/jpeg"
        });
        savePhoto(formData, user.id).then(() => {
          loadProfessionalPhotos();
        });

      } else {
        Alert.alert("", "o tipo da imagem selecionada não é suportada");
      }
    }
  }

  if (loading) {
    return (<Loading/>);
  }
  return (
    <ScrollView
      contentContainerStyle={styles.scrollview}
      showsVerticalScrollIndicator={true}
      scrollEnabled={true}
    >
      <View>
        <FlatList
          data={photos}
          keyExtractor={item => item.id}
          numColumns={2}
          renderItem={({item, index}) => (
            <ProfessionalPhotoGrid item={item} reload={() => loadProfessionalPhotos()}/>
          )}
        />
        {photos.length < 8 &&
        <Button
          title={"Adicionar foto"}
          style={{marginTop: 10}}
          textColor={{color: Theme.textColor.dark}}
          onPress={() => pickImage()}
        />}
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  scrollview: {
    backgroundColor: Theme.scrollViewBackgroundColor,
    padding: 16,
    flex: 1
  }
});
