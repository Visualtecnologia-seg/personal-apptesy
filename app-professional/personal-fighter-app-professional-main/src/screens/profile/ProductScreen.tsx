import React, {useEffect, useState} from "react";
import {FlatList, ScrollView, StyleSheet, View} from "react-native";
import {Theme} from "../../theme";
import {Button} from "../../components";
import {useAuth} from "../../context/AuthContext";
import {Loading} from "../../components/general/loading";
import {useIsFocused, useNavigation} from "@react-navigation/native";
import {ProductModel} from "../../model/ProductModel";
import {getProducts, getProfessionalProducts, saveProducts} from "../../services/ProductService";
import {ProductListItem} from "./component/ProductListItem";

type TProps = {};

export const ProductScreen: React.FC<TProps> = props => {
  /* Default */
  const isFocused = useIsFocused();
  const {user} = useAuth();
  const [loading, setLoading] = useState(true);
  const navigation = useNavigation();
  /* Products */
  const [products, setProducts] = useState<ProductModel[]>([]);
  const [professionalProducts, setProfessionalProducts] = useState<ProductModel[]>([]);

  useEffect(() => {
    setLoading(true);
    if (isFocused) {
      loadAll();
    }
  }, [isFocused]);

  function loadAll() {
    setLoading(true);
    getProducts().then(res => {
      setProducts(res.data.content);
      getProfessionalProducts(user.id).then(res => {
        setProfessionalProducts(res.data);
        setLoading(false);
      });
    });
  }

  function getChecked(product) {
    const checked = professionalProducts?.filter(p => p?.id === product?.id);
    return checked?.length > 0;
  }

  function handleProfessionalProducts(item) {
    const hasProduct = professionalProducts.filter(p => p.id === item.id).length > 0;
    if (hasProduct) {
      setProfessionalProducts(professionalProducts.filter(p => p.id !== item.id));
    } else {
      setProfessionalProducts([...professionalProducts, item]);
    }
  }

  function doSaveProducts() {
    setLoading(true);
    saveProducts(professionalProducts, user.id).then(() => {
      loadAll();
    });
  }

  if (loading) {
    return (<Loading/>);
  }

  return (
    <ScrollView
      contentContainerStyle={styles.scrollview}
      scrollEnabled={true}
    >
      <View>
        <FlatList
          data={products}
          numColumns={1}
          keyExtractor={item => item.id}
          renderItem={({item, index}) => (
            <ProductListItem
              title={item.name}
              index={index}
              checked={getChecked(item)}
              onPress={() => handleProfessionalProducts(item)}/>
          )}
        />
        <Button
          title={"Salvar"}
          style={{marginTop: 10}}
          textColor={{color: Theme.textColor.dark}}
          onPress={() => doSaveProducts()}
        />
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
