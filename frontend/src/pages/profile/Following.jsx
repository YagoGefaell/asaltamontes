import { getFollowingRequest } from "../../features/users/services/users.service";
import FollowListPage from "./FollowListPage";

function Following() {
  return (
    <FollowListPage
      type="following"
      title="Siguiendo"
      fetchFunction={getFollowingRequest}
      emptyMessage="No sigues a nadie aún"
      searchPlaceholder="Buscar en siguiendo..."
      resultsCountMessage={(count) => `Siguiendo a ${count} usuarios`}
    />
  );
}

export default Following;