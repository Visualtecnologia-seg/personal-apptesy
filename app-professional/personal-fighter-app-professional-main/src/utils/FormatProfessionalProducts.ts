import {ProfessionalModel} from "../model";

export const formatProfessionalProducts = (professional: ProfessionalModel) => {

  let products = "";
  if (professional.products !== null) {
    professional.products.map(p => {
      // @ts-ignore
      products = products + p.name + " - "
    })
    return products = products.substring(0, products.length - 2)
  } else {
    return products;
  }

}