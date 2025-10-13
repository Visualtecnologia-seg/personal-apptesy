import React from "react";
import {StyleSheet, View} from "react-native";
import {YELLOW} from "../../theme";
import {Feather} from "@expo/vector-icons";

type TProps = {
  rating: number;
  size: number
};

export const ProfessionalRating: React.FC<TProps> = props => {
  let stars = []
  for (let i = 0; i < props.rating; ++i) {
    stars.push(<Feather key={i} name="star" size={props.size} color={YELLOW} style={{marginRight: 2}}/>)
  }

  return (
    <View style={styles.mainContainer}>
      {stars}
    </View>
  )
}

const styles = StyleSheet.create({
  mainContainer: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center"
  },
});
