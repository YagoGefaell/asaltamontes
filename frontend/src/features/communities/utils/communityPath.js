// URL of a community in the app (T14): by slug, encoded just in case; the name only for data without slug
export function communityPath(community, suffix = '') {
  const key = community?.slug || community?.name || ''
  return `/communities/${encodeURIComponent(key)}${suffix}`
}
