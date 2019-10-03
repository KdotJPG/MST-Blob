# MST Blob
Creates a connected blob-like image by analytically integrating the edges of a Minimum Spanning Tree through a blur kernel, and thresholding.
Inspired by [a post on /r/proceduralgeneration](https://www.reddit.com/r/proceduralgeneration/comments/dbtk88/looking_of_ideasadvice_for_generating_this_sort/)

## Sample result
![Sample image without tree shown](sample.png?raw=true)

## Sample result with tree
![Sample image with tree shown](sample_with_tree.png?raw=true)

## Sample result, raw output
![Sample image with tree shown](sample_raw_output.png?raw=true)

### Usage
* javac MSTBlob.java && java MSTBlob
