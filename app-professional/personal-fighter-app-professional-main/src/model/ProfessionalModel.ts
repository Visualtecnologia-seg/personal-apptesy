import {ProductModel} from "./ProductModel";

export interface ProfessionalModel {
  id?: string;
  cpf?: string;
  name?: string;
  surname?: string;
  products?: ProductModel[];
  avatarUrl?: string;
  about?: string;
  photos?: PhotoModel[];
  rating?: RatingModel;
  price?: number;
  expoToken?: string;
  showAgenda?: boolean;
}

export interface RatingModel {
  average: number;
  count: number;
}

export interface PhotoModel {
  id: string;
  imageUrl: string;
}


