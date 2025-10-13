import React from "react";
import {ScrollView, StyleSheet} from "react-native";

import HTML from "react-native-render-html";
import {termsOfService} from "./component/PrivacyPolicyAndTermsOfService";
import {themeStyles} from "../../config/theme/Styles";


interface Props {
}

export const TermsOfService: React.FC<Props> = props => {

  return (
    <ScrollView
      style={themeStyles.text}>
      <HTML
        baseFontStyle={{color: "white"}}
        source={{html: termsOfService}}
      />
    </ScrollView>
  );
};
const styles = StyleSheet.create({});
