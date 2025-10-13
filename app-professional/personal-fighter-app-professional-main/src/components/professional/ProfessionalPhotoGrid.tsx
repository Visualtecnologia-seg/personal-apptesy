import React, {useState} from "react";
import {Image, StyleSheet, TouchableOpacity} from "react-native";
import {RED, SCREEN_WIDTH} from "../../theme";
import {Overlay} from "react-native-elements";
import {Button} from "../general/buttons";
import {removePhoto} from "../../services/PhotoService";

interface Props {
  item: any;
  reload: any;
}

export const ProfessionalPhotoGrid: React.FC<Props> = props => {
  const imageUrl = props.item.imageUrl;
  const [visible, setVisible] = useState(false);

  const toggleOverlay = () => {
    setVisible(!visible);
  };

  function doRemovePhoto() {
    removePhoto(props.item.id).then(() => {
      setVisible(!visible);
      props.reload()
    })
  }

  return (
    <>
      <TouchableOpacity
        onPress={() => {
          toggleOverlay();
        }}
        style={styles.touchableOpacity}
      >
        <Image
          style={styles.image}
          resizeMode="contain"
          source={{uri: imageUrl.toString()}}
          fadeDuration={2000}
        />
      </TouchableOpacity>
      <Overlay
        isVisible={visible}
        onBackdropPress={toggleOverlay}
        fullScreen={false}
        overlayStyle={{backgroundColor: "rgba(40,38,49,0.9)"}}
        style={{flex: 1}}
      >
        <TouchableOpacity onPress={() => {
          toggleOverlay();
        }}>
          <Image
            style={styles.overlay}
            resizeMode="contain"
            source={{uri: imageUrl}}
            fadeDuration={500}
          />
          <Button
            title={"Deletar"}
            style={{backgroundColor: RED, alignSelf: "center"}}
            onPress={() => doRemovePhoto()}
          />
        </TouchableOpacity>
      </Overlay>
    </>
  );
};

const styles = StyleSheet.create({
  image: {
    flex: 1,
    borderRadius: 8
  },
  touchableOpacity: {
    flex: 1,
    height: 120,
    marginHorizontal: 5,
    marginBottom: 5
  },
  overlay: {
    flex: 1,
    width: SCREEN_WIDTH
  }
});
