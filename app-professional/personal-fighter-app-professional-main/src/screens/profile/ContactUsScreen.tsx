import React, {useEffect, useState} from "react";
import {ScrollView, StyleSheet} from "react-native";

import {Loading} from "../../components/general/loading";
import {themeStyles} from "../../config/theme/Styles";
import {useAuth} from "../../context/AuthContext";

interface Props {
}


export const ContactUsScreen: React.FC<Props> = props => {
  const [loading, setLoading] = useState<boolean>(true);
  const {user, setUser} = useAuth();

  useEffect(() => {
    setTimeout(() => setLoading(false), 500);
  }, [user]);

  if (loading) {
    return (<Loading/>);
  }

  return (
    <ScrollView
      contentContainerStyle={themeStyles.text}
    >


    </ScrollView>
  );
};
const styles = StyleSheet.create({});
