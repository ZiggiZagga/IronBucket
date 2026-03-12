import { gql } from '@apollo/client';

export const GET_BUCKETS = gql`
  query GetBuckets {
    listBuckets {
      name
      creationDate
      ownerTenant
    }
  }
`;

export const GET_ROOT_ITEMS = gql`
  query GetRootItems {
    rootItems {
      id
      name
      description
      parentId
    }
  }
`;
