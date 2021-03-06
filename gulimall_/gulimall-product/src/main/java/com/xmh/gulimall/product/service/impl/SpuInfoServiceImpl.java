package com.xmh.gulimall.product.service.impl;

import com.google.j2objc.annotations.AutoreleasePool;
import com.mysql.cj.util.StringUtils;
import com.xmh.common.constant.ProductConstant;
import com.xmh.common.to.SkuHasStockTo;
import com.xmh.common.to.SkuReductionTo;
import com.xmh.common.to.SpuBoundTo;
import com.xmh.common.to.es.SkuEsModel;
import com.xmh.common.utils.R;
import com.xmh.gulimall.product.entity.*;
import com.xmh.gulimall.product.feign.CouponFeignService;
import com.xmh.gulimall.product.feign.SearchFeignService;
import com.xmh.gulimall.product.feign.WareFeignService;
import com.xmh.gulimall.product.service.*;
import com.xmh.gulimall.product.vo.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xmh.common.utils.PageUtils;
import com.xmh.common.utils.Query;

import com.xmh.gulimall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    SpuInfoDescService spuInfoDescService;

    @Autowired
    SpuImagesService imagesService;

    @Autowired
    AttrService attrService;

    @Autowired
    ProductAttrValueService productAttrValueService;

    @Autowired
    SkuInfoService skuInfoService;

    @Autowired
    SkuImagesService skuImagesService;

    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    WareFeignService wareFeignService;

    @Autowired
    SearchFeignService searchFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    /*
    //TODO ????????????????????????
     */
    @Transactional
    @Override
    public void saveSpuInfo(SpuSaveVo vo) {
        //1?????????spu????????????`pms_spu_info`
        SpuInfoEntity infoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(vo, infoEntity);
        infoEntity.setCreateTime(new Date());
        infoEntity.setUpdateTime(new Date());
        this.save(infoEntity);

        //2?????????spu???????????????`pms_spu_info_desc`
        List<String> decript = vo.getDecript();
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        descEntity.setSpuId(infoEntity.getId());
        descEntity.setDecript(String.join(",", decript));
        spuInfoDescService.save(descEntity);

        //3?????????spu????????????`pms_spu_images`
        List<String> images = vo.getImages();
        if (images != null && images.size() != 0) {
            List<SpuImagesEntity> collect = images.stream().map(image -> {
                SpuImagesEntity imagesEntity = new SpuImagesEntity();
                imagesEntity.setSpuId(infoEntity.getId());
                imagesEntity.setImgUrl(image);
                return imagesEntity;
            }).collect(Collectors.toList());
            imagesService.saveBatch(collect);
        }

        //4?????????spu???????????????`pms_product_attr_value`
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        List<ProductAttrValueEntity> productAttrValueEntityList = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity valueEntity = new ProductAttrValueEntity();
            valueEntity.setAttrId(attr.getAttrId());
            AttrEntity byId = attrService.getById(attr.getAttrId());
            valueEntity.setAttrName(byId.getAttrName());
            valueEntity.setAttrValue(attr.getAttrValues());
            valueEntity.setQuickShow(attr.getShowDesc());
            valueEntity.setSpuId(infoEntity.getId());
            return valueEntity;
        }).collect(Collectors.toList());
        productAttrValueService.saveBatch(productAttrValueEntityList);


        //5?????????spu???????????????`gulimall_sms`->`sms_spu_bounds`
        Bounds bounds = vo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds, spuBoundTo);
        spuBoundTo.setSpuId(infoEntity.getId());
        R r = couponFeignService.saveSpuBounds(spuBoundTo);
        if (r.getCode() != 0){
            log.error("????????????spu??????????????????");
        }

        //6?????????spu???????????????sku??????
        List<Skus> skus = vo.getSkus();
        if (skus != null && skus.size() > 0) {
            skus.forEach(item -> {
                String defaultImg = "";
                //?????????????????????
                for (Images image : item.getImages()) {
                    if (image.getDefaultImg() == 1) {
                        defaultImg = image.getImgUrl();
                    }
                }
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties(item, skuInfoEntity);
                skuInfoEntity.setSpuId(infoEntity.getId());
                skuInfoEntity.setBrandId(infoEntity.getBrandId());
                skuInfoEntity.setCatalogId(infoEntity.getCatalogId());
                skuInfoEntity.setSaleCount(0L);
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                //6.1???sku???????????????`pms_sku_info`
                skuInfoService.save(skuInfoEntity);

                //6.2???sku???????????????`pms_sku_images`
                Long skuId = skuInfoEntity.getSkuId();

                List<SkuImagesEntity> skuImagesEntities = item.getImages().stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity -> {
                    //??????true??????????????????false????????????
                    return !StringUtils.isNullOrEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                skuImagesService.saveBatch(skuImagesEntities);

                //6.3???sku?????????????????????`pms_sku_sale_attr_value`
                List<Attr> attr = item.getAttr();
                List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attr.stream().map(a -> {
                    SkuSaleAttrValueEntity skuSaleAttrValueEntity = new SkuSaleAttrValueEntity();
                    skuSaleAttrValueEntity.setSkuId(skuId);
                    BeanUtils.copyProperties(a, skuSaleAttrValueEntity);
                    return skuSaleAttrValueEntity;
                }).collect(Collectors.toList());
                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);


                //6.4???sku???????????????????????????`gulimall_sms`->`sms_sku_ladder`/`sms_sku_full_reduction`/`sms_member_price`
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(item, skuReductionTo);
                skuReductionTo.setSkuId(infoEntity.getId());
                if (skuReductionTo.getFullCount() > 0 || skuReductionTo.getFullPrice().compareTo(new BigDecimal("0")) == 1){
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                    if (r1.getCode() != 0){
                        log.error("??????????????????????????????");
                    }
                }
            });
        }
    }

    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {
        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();

        String catelogId = (String) params.get("catelogId");
        if (!StringUtils.isNullOrEmpty(catelogId) && !"0".equalsIgnoreCase(catelogId)){
            wrapper.eq("catalog_id", catelogId);
        }

        String brandId = (String) params.get("brandId");
        if (!StringUtils.isNullOrEmpty(brandId) && !"0".equalsIgnoreCase(brandId)){
            wrapper.eq("brand_id", brandId);
        }

        String status = (String) params.get("status");
        if (!StringUtils.isNullOrEmpty(status)){
            wrapper.eq("publish_status", status);
        }

        String key = (String) params.get("key");
        if (!StringUtils.isNullOrEmpty(key)){
            wrapper.and((w) -> {
                w.eq("id", key).or().like("spu_name", key);
            });
        }

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    /**
     * ????????????
     * @param spuId
     */
    @Override
    public void up(Long spuId) {
        //1???????????????spuid???????????????sku???????????????????????????
        List<SkuInfoEntity> skuInfoEntities = skuInfoService.getSkusBySpuId(spuId);
        List<Long> skuIds = skuInfoEntities.stream().map(item -> item.getSkuId()).collect(Collectors.toList());

        //TODO 3??????????????????????????????????????? brandName,brandImg,catalogName
        Long brandId = skuInfoEntities.get(0).getBrandId();
        BrandEntity brandEntity = brandService.getById(brandId);
        String brandName = brandEntity.getName();
        String brandImg = brandEntity.getLogo();
        String cateName = categoryService.getById(skuInfoEntities.get(0).getCatalogId()).getName();

        //TODO 4???????????????sku?????????????????????????????????????????????
        List<ProductAttrValueEntity> baseAttrs = productAttrValueService.baseAttrlistForSpu(spuId);
        List<Long> attrIds = baseAttrs.stream().map(baseAttr -> {
            return baseAttr.getAttrId();
        }).collect(Collectors.toList());
        List<AttrEntity> searchAttrs = attrService.getSearchAttr(attrIds);
        List<Long> searchAttrIds = searchAttrs.stream().map(searchAttr -> searchAttr.getAttrId()).collect(Collectors.toList());
        //????????????????????????id
        Set<Long> idSet = new HashSet<>(searchAttrIds);
        //??????set?????????spu???attr?????????????????????????????????attr
        List<SkuEsModel.Attr> attrsList = baseAttrs.stream().filter(item -> {
            return idSet.contains(item.getAttrId());
        }).map(item -> {
            SkuEsModel.Attr attr = new SkuEsModel.Attr();
            BeanUtils.copyProperties(item, attr);
            return attr;
        }).collect(Collectors.toList());


        //TODO 1??????????????????????????????????????????????????????  hasStock
        //???????????????????????????sku???????????????
        Map<Long, Boolean> stockMap = null;
        try {
            List<SkuHasStockTo> hasStock = wareFeignService.hasStock(skuIds);
            stockMap = hasStock.stream().collect(Collectors.toMap(item -> item.getSkuId(), item -> item.getHasStock()));
        }catch (Exception e) {
            log.error("????????????????????????,??????:{}", e);
        }


        //2????????????sku????????????SkuEsModel
        Map<Long, Boolean> finalStockMap = stockMap;
        List<SkuEsModel> skuEsModels = skuInfoEntities.stream().map(sku -> {
            SkuEsModel skuEsModel = new SkuEsModel();
            BeanUtils.copyProperties(sku, skuEsModel);
            skuEsModel.setSkuPrice(sku.getPrice());
            skuEsModel.setSkuImg(sku.getSkuDefaultImg());
            //1??????????????????????????????????????????????????????  hasStock
            if (finalStockMap == null){
                skuEsModel.setHasStock(false);
            }else{
                skuEsModel.setHasStock(finalStockMap.get(sku.getSkuId()));
            }


            //2???????????????
            skuEsModel.setHotScore(0L);

            //3??????????????????????????????????????? brandName,brandImg,catalogName
            skuEsModel.setBrandName(brandName);
            skuEsModel.setBrandImg(brandImg);
            skuEsModel.setCatalogName(cateName);

            //4???????????????sku?????????????????????????????????????????????
            skuEsModel.setAttrs(attrsList);
            return skuEsModel;
        }).collect(Collectors.toList());

        //TODO 5?????????????????????es????????????
        R r = searchFeignService.productStatusUp(skuEsModels);
        if (r.getCode() == 0){
            //??????????????????
            //TODO 6???????????????spu?????????
            baseMapper.updataSpuStatus(spuId, ProductConstant.StatusEnum.SPU_UP.getCode());
        }else{
            //??????????????????
            //TODO 7?????????????????????????????????
        }
    }

}