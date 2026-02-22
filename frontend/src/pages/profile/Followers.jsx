import { getFollowersRequest } from "../../features/users/services/users.service";
import FollowListPage from "./FollowListPage";

function Followers() {
  return (
    <FollowListPage
      type="followers"
      title="Seguidores"
      fetchFunction={getFollowersRequest}
      emptyMessage="No tienes seguidores aún"
      searchPlaceholder="Buscar en seguidores..."
      resultsCountMessage={(count) => `${count} seguidores`}
    />
  );
}

export default Followers;