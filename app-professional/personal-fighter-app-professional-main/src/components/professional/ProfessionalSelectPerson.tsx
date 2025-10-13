import React, {useEffect, useState} from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {Theme, YELLOW} from "../../theme";
import {Divider} from "../general/divider";
import {Feather} from "@expo/vector-icons";
import {infoOrder} from "../../utils/InfoOrder";

type TProps = {
  onSelectedOrder: (customers, price, gender) => void;
};

export const ProfessionalSelectPerson: React.FC<TProps> = props => {
  const [numberOfCustomers, setNumberOfCustomers] = useState(1);
  const [male, setMale] = useState(true)
  const [female, setFemale] = useState(true)

  function handleSelectNumberOfCustomers(action) {
    if (action === "plus") {
      if (numberOfCustomers >= 15) {
        setNumberOfCustomers(15)
      } else {
        setNumberOfCustomers(numberOfCustomers + 1)
      }
    } else {
      if (numberOfCustomers <= 1) {
        setNumberOfCustomers(1)
      } else {
        setNumberOfCustomers(numberOfCustomers - 1)
      }
    }
  }

  const handleSelectGender = () => {
    if (male && female || !male && !female) {
      return "ANY";
    } else if (male && !female) {
      return "MALE";
    } else if (female && !male) {
      return "FEMALE";
    }
  }

  const info = infoOrder(numberOfCustomers)

  useEffect(() => {
    props.onSelectedOrder(numberOfCustomers, info.price, handleSelectGender())
  }, [numberOfCustomers, male, female])

  return (
    <View style={styles.itemRowContainer}>
      <View style={{flexDirection: 'row'}}>
        <View style={{flex: 1}}>
          <Text style={styles.text}>Quantos alunos?</Text>
        </View>
        <View style={styles.buttonView}>
          <TouchableOpacity style={styles.button} onPress={() => handleSelectNumberOfCustomers("minus")}>
            <Feather name="minus-circle" size={20} color={YELLOW}/>
          </TouchableOpacity>
          <Text style={styles.text}>{numberOfCustomers}</Text>
          <TouchableOpacity style={[styles.button, {marginRight: 0}]}
                            onPress={() => handleSelectNumberOfCustomers("plus")}>
            <Feather name="plus-circle" size={20} color={YELLOW}/>
          </TouchableOpacity>
        </View>
      </View>

      <Divider style={{marginHorizontal: 0, marginVertical: 10}}/>
      <View style={{flexDirection: "row"}}>
        <View style={{flexDirection: "row"}}>
          <Feather name={info.icon} size={20} color={YELLOW}/>
          <Text style={[styles.text, {textAlign: "right", marginLeft: 10}]}>{info.group}</Text>
        </View>
        <View style={{flex: 1}}>
          <Text style={[styles.text, {textAlign: "right"}]}>R$ {info.price},00</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  itemRowContainer: {
    flex: 1,
    backgroundColor: Theme.card.backgroundColor,
    marginHorizontal: 16,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 8,
  },
  button: {
    alignItems: "center",
    justifyContent: "center",
    marginHorizontal: 10
  },
  buttonView: {
    flexDirection: "row"
  },
  text: {
    color: Theme.textColor.primary,
    fontSize: Theme.textSize.primary
  },
  titleText: {
    color: Theme.textColor.primary,
    fontSize: Theme.textSize.section,
    marginBottom: 10,
    textAlign: "center"
  }
});
